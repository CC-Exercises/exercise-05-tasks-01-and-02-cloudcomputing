package de.ustutt.iaas.cc.core.schedulingStrategy;

import javax.ws.rs.client.WebTarget;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobin implements SchedulingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final List<WebTarget> resources;

    public RoundRobin(List<WebTarget> resources) {
        this.resources = resources;
    }

    @Override
    public WebTarget next() {
        int index = counter.incrementAndGet() % resources.size();

        return resources.get(index);
    }
}
