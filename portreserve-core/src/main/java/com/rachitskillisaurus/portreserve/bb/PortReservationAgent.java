package com.rachitskillisaurus.portreserve.bb;

import com.rachitskillisaurus.portreserve.bootstrap.HasDelegate;
import com.rachitskillisaurus.portreserve.internal.PortReservationLogger;
import com.rachitskillisaurus.portreserve.internal.OriginalSocksSocketImplFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader;
import net.bytebuddy.dynamic.ClassFileLocator.Resolution;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.MethodNameEqualityResolver;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.SocketImpl;
import java.util.Collections;
import java.util.HashMap;

import static net.bytebuddy.implementation.MethodDelegation.to;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class PortReservationAgent {
    public final static String SOCKET_IMPL_CLASSNAME = "java.net.PortReserveSocketImpl";

    public static void premain(String arguments, Instrumentation instrumentation) {
        try {
            File bytecodeDir = createTempDirectory();
            bytecodeDir.deleteOnExit();
            final Unloaded<? extends SocketImpl> unloadedSocketClass = generateSocketImpl();
            injectClassAndDependenciesToBootstrap(unloadedSocketClass, bytecodeDir, instrumentation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Unloaded<? extends SocketImpl> generateSocketImpl() {
        MethodDelegation delegateField =
                MethodDelegation.toInstanceField(SocketImpl.class, "delegate")
                    .defineAmbiguityResolver(MethodNameEqualityResolver.INSTANCE);

        return new ByteBuddy()
                .subclass(SocketImpl.class)
                .name(SOCKET_IMPL_CLASSNAME)
                .method(ElementMatchers.<MethodDescription>any()).intercept(delegateField)
                .implement(HasDelegate.class).intercept(FieldAccessor.ofBeanProperty())
                .constructor(any())
                .intercept(to(PortReserveSocketImplConstructor.class).andThen(SuperMethodCall.INSTANCE))
                .method(named("bind")).intercept(to(PortReserveSocketImplMethods.class))
                .method(named("close")).intercept(to(PortReserveSocketImplMethods.class))
                .make();
    }

    private static void injectClassAndDependenciesToBootstrap(final Unloaded<? extends SocketImpl> unloadedSocketClass,
                                                              File bytecodeDir, Instrumentation instrumentation) {
        injectWithoutLoad("com.rachitskillisaurus.portreserve.bootstrap.PortReservationRegistry",
                          bytecodeDir, instrumentation);
        injectWithoutLoad("com.rachitskillisaurus.portreserve.bootstrap.PortReservationInternal",
                          bytecodeDir, instrumentation);
        injectWithoutLoad("com.rachitskillisaurus.portreserve.bootstrap.HasDelegate",
                          bytecodeDir, instrumentation);

        ClassInjector.UsingInstrumentation
                .of(bytecodeDir, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation).inject(
                    new HashMap<TypeDescription, byte[]>() {{
                        put(unloadedSocketClass.getTypeDescription(), unloadedSocketClass.getBytes());
                        put(new TypeDescription.ForLoadedType(PortReservationLogger.class),
                            ClassFileLocator.ForClassLoader.read(PortReservationLogger.class).resolve());
                        put(new TypeDescription.ForLoadedType(PortReserveSocketImplConstructor.class),
                            ClassFileLocator.ForClassLoader.read(PortReserveSocketImplConstructor.class).resolve());
                        put(new TypeDescription.ForLoadedType(PortReserveSocketImplMethods.class),
                            ClassFileLocator.ForClassLoader.read(PortReserveSocketImplMethods.class).resolve());
                        put(new TypeDescription.ForLoadedType(OriginalSocksSocketImplFactory.class),
                            ClassFileLocator.ForClassLoader.read(OriginalSocksSocketImplFactory.class).resolve());
                    }}
        );
    }

    private static void injectWithoutLoad(String className, File bytecodeDir, Instrumentation instrumentation) {
        try {
            final TypeDescription td = TypePool.Default.ofClassPath().describe(className).resolve();
            final Resolution locate = ForClassLoader.ofClassPath().locate(className);
            ClassInjector.UsingInstrumentation
                    .of(bytecodeDir, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation).inject(
                    Collections.singletonMap(td, locate.resolve()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File createTempDirectory() throws IOException {
        final File temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
        if (!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }
        if (!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }
        return temp;
    }
}
