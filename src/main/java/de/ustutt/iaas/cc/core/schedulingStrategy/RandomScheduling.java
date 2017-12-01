package de.ustutt.iaas.cc.core.schedulingStrategy;

import javax.ws.rs.client.WebTarget;
import java.util.List;
import java.util.Random;

public class RandomScheduling implements SchedulingStrategy {

    private final List<WebTarget> resources;
    private final Random random = new Random();

    public RandomScheduling(List<WebTarget> resources) {
        this.resources = resources;
    }

    @Override
    public WebTarget next() {
        int index = random.nextInt(resources.size());
        return null;
    }
}
