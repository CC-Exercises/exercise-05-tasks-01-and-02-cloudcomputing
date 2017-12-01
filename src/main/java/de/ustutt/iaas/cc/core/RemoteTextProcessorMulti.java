package de.ustutt.iaas.cc.core;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

public class RemoteTextProcessorMulti implements ITextProcessor {

    private final SchedulingStrategy schedulingStrategy;

    public RemoteTextProcessorMulti(List<String> textProcessorResources, Client client) {
        super();
        List<WebTarget> textProcessors = generateWebTargetsFromSourceList(textProcessorResources, client);
        this.schedulingStrategy = new RoundRobin(textProcessors);
    }

    private List<WebTarget> generateWebTargetsFromSourceList(List<String> textProcessorResources, Client client) {
        List<WebTarget> result = new ArrayList<>();
        for (String s : textProcessorResources) {
            result.add(client.target(s));
        }
        return result;
    }

    @Override
    public String process(String text) {
        WebTarget textProcessor = schedulingStrategy.next();

        return textProcessor.request(MediaType.TEXT_PLAIN).post(Entity.entity(text, MediaType.TEXT_PLAIN), String.class);
    }
}
