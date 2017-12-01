package de.ustutt.iaas.cc.core;

import javax.ws.rs.client.WebTarget;

public interface SchedulingStrategy {
    WebTarget next();
}
