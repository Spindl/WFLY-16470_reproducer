package com.nts.reproducer.server;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

@ApplicationScoped
@Path("reproducer")
@Produces("application/json")
public class RestService {

    private static final int DEFAULT_TIMES = 10000;

    private final Runtime runtime = Runtime.getRuntime();

    private final Client client = ClientBuilder.newClient();

    @GET
    @Path("configureClients")
    public double createClientsWithTracing(@QueryParam("times") Integer times) {

        // Do a few requests, the automatically added OpenTelemetryClientRequestFilter will cause the leak
        for (int i = 0; i < (null != times ? times : DEFAULT_TIMES); i++) {
            // The leak occurs because every request creates a new dependent instance of the OpenTelemetry object in the filter
            client.target("http://localhost:8080/rest/reproducer/anotherEndpoint").request().get();
        }

        // Trigger a full Garbage Collection run to get the real blocked memory size later.
        System.gc();
        // Calculate the heap utilization and send it back to the client
        return ((1d * (runtime.totalMemory() - runtime.freeMemory())) / runtime.maxMemory()) * 100;
    }

    @GET
    @Path("anotherEndpoint")
    public void doNothing() {
        // This endpoint is only used as target for the request that causes the leak, it does not have to do anything
    }
}
