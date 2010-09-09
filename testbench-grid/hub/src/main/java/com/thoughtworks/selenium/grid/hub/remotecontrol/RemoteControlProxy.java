package com.thoughtworks.selenium.grid.hub.remotecontrol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.thoughtworks.selenium.grid.HttpClient;
import com.thoughtworks.selenium.grid.HttpParameters;
import com.thoughtworks.selenium.grid.Response;
import com.thoughtworks.selenium.grid.hub.HubServer;

/**
 * Local interface to a real remote control running somewhere in the grid.
 */
public class RemoteControlProxy {

    private static final Log LOGGER = LogFactory.getLog(HubServer.class);

    private static final int NUM_POLL_RETRIES = 3;

    private boolean sessionInProgress;
    private final HttpClient httpClient;
    private final String environment;
    private final String host;
    private final int port;
    private long startTime;

    public RemoteControlProxy(String host, int port, String environment,
            HttpClient httpClient) {
        if (null == host) {
            throw new IllegalArgumentException("host cannot be null");
        }
        if (null == environment) {
            throw new IllegalArgumentException("environment cannot be null");
        }
        this.host = host;
        this.port = port;
        this.environment = environment;
        sessionInProgress = false;
        this.httpClient = httpClient;
    }

    public String host() {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostName();
        } catch (UnknownHostException e) {
            // return host ip
        }
        return host;
    }

    public int port() {
        return port;
    }

    public String runtime() {
        long time = System.currentTimeMillis() - startTime;
        return (time / 1000) + "," + (time % 1000) / 10 + " sec";
    }

    public String environment() {
        return environment;
    }

    public String remoteControlPingURL() {
        return remoteControlURLFor("heartbeat");
    }

    public String remoteControlDriverURL() {
        return remoteControlURLFor("driver/");
    }

    public String remoteControlURLFor(String path) {
        return "http://" + host + ":" + port + "/selenium-server/" + path;
    }

    public Response forward(HttpParameters parameters) throws IOException {
        // If no session return failure Response for timeout.
        if (!sessionInProgress) {
            return new Response("Test failed due to timeout.");
        }
        return httpClient.post(remoteControlDriverURL(), parameters);
    }

    @Override
    public String toString() {
        return "[RemoteControlProxy " + host + ":" + port + "#" + environment
                + "/" + sessionInProgress + "]";
    }

    // Added environment to equals so same RC can register multiple environments
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final RemoteControlProxy otherRemoteControl = (RemoteControlProxy) other;
        return host.equals(otherRemoteControl.host)
                && port == otherRemoteControl.port
                && environment.equals(otherRemoteControl.environment);
    }

    @Override
    public int hashCode() {
        return (host + port).hashCode();
    }

    public boolean sessionInProgress() {
        return sessionInProgress;
    }

    public void registerNewSession() {
        if (sessionInProgress) {
            throw new IllegalStateException(
                    "Exceeded concurrent session max for " + toString());
        }
        sessionInProgress = true;
        startTime = System.currentTimeMillis();
    }

    public void unregisterSession() {
        if (!sessionInProgress) {
            throw new IllegalStateException(
                    "Unregistering session on an idle remote control : "
                            + toString());
        }
        sessionInProgress = false;
    }

    public void terminateSession(String sessionId) {
        try {
            Map<String, String[]> params = new HashMap<String, String[]>();
            params.put("cmd", new String[] { "testComplete" });
            params.put("sessionId", new String[] { sessionId });

            forward(new HttpParameters(params));
        } catch (IOException e) {
            LOGGER.warn("Exception telling remote control to kill its session:"
                    + e.getMessage());
        }
    }

    public boolean canHandleNewSession() {
        if (sessionInProgress()) {
            return false;
        }
        return true;
    }

    public int unreliable() {
        final Response response;

        try {
            LOGGER.debug("Polling Remote Control at " + host + ":" + port);
            response = httpClient.get(remoteControlPingURL());
        } catch (Exception e) {
            LOGGER.warn("Remote Control at " + host + ":" + port
                    + " is unresponsive");
            return 500;
        }
        if (response.statusCode() != 200) {
            LOGGER.warn("Remote Control at " + host + ":" + port
                    + " did not respond correctly");
            return response.statusCode();
        }
        return 200;
    }

}
