package com.rachitskillisaurus.portreserve;

/**
 * Callback interface for performing a reservation-to-owner socket transfer.  Cod e that runs within the "{@code
 * transfer}"
 * method is allowed to "re-bind" the reserved socket; any code outside of this transfer method is not.
 */
public interface TransferCallback<T> {
    /**
     * Perform a port transfer operation
     *
     * @return Any transfer result, for example instance of created server
     * @throws Exception if an exception transferring port occurs
     */
    T transfer() throws Exception;
}
