package com.nts.reproducer.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class Main {

    private static final int BATCH_SIZE = 10000;
    private static final int TARGET_HEAP_UTILIZATION = 80;

    public static void main(String[] args) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        double initiallyUsedHeap = getDebouncedHeapSize(client);

        System.out.printf("Initially, %.2f%% of heap were used%n", initiallyUsedHeap);

        if (initiallyUsedHeap < TARGET_HEAP_UTILIZATION) {
            triggerLeak(client, 1);
            System.out.println("Sending initial batch");
            step(client);
            double heapUtilizationAfterFirstBatch = getDebouncedHeapSize(client);
            int initialBatchHeapUsage = (int) Math.ceil(heapUtilizationAfterFirstBatch - initiallyUsedHeap);

            if (heapUtilizationAfterFirstBatch < TARGET_HEAP_UTILIZATION) {
                System.out.printf("%d instances ~= %d%% heap%n", BATCH_SIZE, initialBatchHeapUsage);
                int stepCount = (int) Math.ceil(((TARGET_HEAP_UTILIZATION - heapUtilizationAfterFirstBatch) / initialBatchHeapUsage));
                System.out.printf("Sending ~ %d more batches with size %d to reach target utilization %d%%%n", stepCount, BATCH_SIZE,
                        TARGET_HEAP_UTILIZATION);

                // Loop until the utilization is reached
                int takenSteps = 0;
                int maxSteps = stepCount * 2;
                while (step(client) < TARGET_HEAP_UTILIZATION && takenSteps < maxSteps) {
                    takenSteps++;
                }

                if (takenSteps < maxSteps) {
                    System.out.println("Done, seems that a memory leak is present.");
                } else if (getDebouncedHeapSize(client) - initiallyUsedHeap >= 10) {
                    System.out.println("Stopping, but seems that a memory leak is present.");
                } else {
                    System.out.println("Stopping, seems that no memory leak is present.");
                }
            } else {
                System.out.printf("... which already is past the target utilization of %d%%", TARGET_HEAP_UTILIZATION);
            }
        } else {
            System.out.printf("... which already is past the target utilization of %d%%", TARGET_HEAP_UTILIZATION);
        }
    }

    private static double step(HttpClient client) throws IOException, InterruptedException {
        System.out.printf("Triggering memory leak for %d instances...", BATCH_SIZE);
        double currentlyUsedHeap = triggerLeak(client, BATCH_SIZE);
        System.out.printf("now %.2f%% of heap are blocked%n", currentlyUsedHeap);
        return currentlyUsedHeap;
    }

    private static double getDebouncedHeapSize(HttpClient client) throws IOException, InterruptedException {
        triggerLeak(client, 1);
        Thread.sleep(100);
        triggerLeak(client, 1);
        Thread.sleep(100);
        return triggerLeak(client, 1);
    }

    private static double triggerLeak(HttpClient client, int times) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder().timeout(Duration.of(5, ChronoUnit.MINUTES))
                        .uri(URI.create("http://localhost:8080/rest/reproducer/configureClients?times=" + times)).GET().build();

        return Double.parseDouble(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }
}
