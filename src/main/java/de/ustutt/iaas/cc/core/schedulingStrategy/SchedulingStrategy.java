package de.ustutt.iaas.cc.core.schedulingStrategy;

import javax.ws.rs.client.WebTarget;

public interface SchedulingStrategy {
    WebTarget next();
}
