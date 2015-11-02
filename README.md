PortReserve [![Build Status](https://drone.io/github.com/ProfessorEugene/PortReserve/status.png)](https://drone.io/github.com/ProfessorEugene/PortReserve/latest) 
===========
A simple utility for allowing java application to reserve ports.  This type of functionality is useful when writing functional testing that needs to scaffold many different TCP services on any available port.  

Usage
=====
Add PortReserve as a maven dependency:
```xml
<dependency>
	<groupId>com.rachitskillisaurus.portreserve</groupId>
	<artifactId>portreserve</artifactId>
	<version>1.0.0</version>
</dependency>
```
Alternatively, download the jar directly from [here]( https://repo1.maven.org/maven2/com/rachitskillisaurus/portreserve/portreserve/1.0.0/portreserve-1.0.0.jar), obtain slf4j-api > 1.7.6 and cglib > 3.1 and place all three on your classpath.

Reserving a single port:
```java
	/* reserve first free open port above 1024 */
	final PortReservation jettyReservation = PortReservationProvider.get().reserveOpenPort(1024);
	...
	/* create a scaffolding that needs to use this port */
	final org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server(jettyReservation.getPort());
	...
	/* transfer ownership of the port to the jetty server */
	jettyReservation.transfer(new TransferCallback(){
		@Override
		public void transfer() throws Exception{
			/* any sockets created or bound on jettyReservation's port will be transfered to 'server' only within the execution scope of this method */
			server.start();
		}
	});
```
Reserving multiple ports:
```java
	final PortReservation ra = PortReservationProvider.get().reserveOpenPort(1024);
	final PortReservation rb = PortReservationProvider.get().reserveOpenPort(1024);
	myService.setFirstPort(ra.getPort());
	myService.setSecondPort(rb.getPort());
	PortReservation.transfer(new TransferCallback() {
		@Override
		public void transfer() throws Exception {
			myService.start();
		}
	}, ra, rb);
```

The Problem
===========
TCP ports can't really be "reserved"; one can bind a socket to a port, precluding other services from using this port system wide, but you have to unbind/close the socket before you can use this service port again.  The typical way to detect a free server port is to attempt to create a `ServerSocket`, catching any exceptions and later closing/unbinding the first socket that could be bound.  Once a free server port is detected in this manner, it is presuably used to configure a scaffolding that uses this TCP port at some point in the future.  For example, to spin up a jetty server on any availalbe port, one might find a free port, close it, and then later start the jetty server on this port.  The problem is that after a port is located in this manner and before the target scaffolding is initialized, any other thread or process can bind to the supposedly free port, breaking the application.

Most Java server/scaffolding APIs do not support passing in an already bound `Serversocket`, instead expecting a configurable `int` port.  On startup, these scaffoldings will instantiate a `ServerSocket` - either directly or via nio and later attempt to `bind` this socket to the configured port.  The PortReserve library allows the scaffolding code to create and bind a `ServerSocket` as it would normally do, but replaces the socket implementation instance behind the newly created socket with an already bound reservation's instance.

The Solution
============
This solution offers clients a `PortReservation` that comes with a pre-bound socket as well as a `PortReservation#transfer(TransferCallback)` method that allows one to seamlessly transfer ownership of the bound socket to a client.  Internally, the PortReserve library replaces the JDK `SocketImplFactory` with one that returns proxies that can intercept "bind" calls to allow seamless transfer of "reserved" ports to client code.

Port Reservation Pitfalls
=========================
The API used for port reservation supports a standard `reserveOpenPort(int)` method that attempts to find a free port that can be bound on *all* interfaces.  Most simple testing scaffolding librareis such as Jetty, Dumbster, embedded-redis etc., will attempt to do the same thing, making this method typically sufficient.  Unfortunately, it is sometimes important to specify a specific interface address to reserve a port on.  If a machine is multihoned (e.g. has several network interfaces) or supports multiple IP addresses per interface (e.g. IPv4 and IPv6) it is important to create a reservation on the interface that client code intends to bind its `ServerSocket`s on - e.g. 127.0.0.1 for loopback IPv4 or ::1 for loopback IPv6.  Note that it is fully possible to bind two separate `ServerSocket`s to the same port on the same interface if this interface supports both IPv4 and IPv6; take care to select the appropriate interface in such situations.

This library aims at allowing users to reserve ports on specific interfaces and later transfer them; selecting the appropriate interface for the reservation is up to the client.  If there are difficulties with reserving the expected address/port combination, consider running java with the "-Djava.net.preferIPv4Stack=true" CLI argument to prefer IPv4 addresses.  Alternatively, one can reserve the same port on all irrelevant interfaces (i.e. reserve port 1024 on ::1/ipv6 at startup to only allow client reservations on port 1024 on the 127.0.0.1/ipv4 interface) 

Internals
=========
PortReserve uses a `SocketImplFactory` that replaces the `SocketImpl` instances underlying all sockets with CGLib enhacned proxies.  These proxies allow one to detect internal bind calls and transfer "ownership" of a `ServerSocket`.  ThreadLocals and a Map are used to ensure transfer only occurs during appropriate execution state (inside the `TransferCallback#transfer()` method)
