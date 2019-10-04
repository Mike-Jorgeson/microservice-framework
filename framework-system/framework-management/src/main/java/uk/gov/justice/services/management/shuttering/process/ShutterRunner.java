package uk.gov.justice.services.management.shuttering.process;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import uk.gov.justice.services.jmx.api.command.SystemCommand;
import uk.gov.justice.services.management.shuttering.api.ShutteringExecutor;
import uk.gov.justice.services.management.shuttering.api.ShutteringResult;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;

public class ShutterRunner {

    @Inject
    private ShutteringExecutorProvider shutteringExecutorProvider;

    @Inject
    private Logger logger;

    public List<ShutteringResult> runShuttering(final UUID commandId, final SystemCommand systemCommand) {
        return shutteringExecutorProvider.getShutteringExecutors().stream()
                .filter(ShutteringExecutor::shouldShutter)
                .map(shutteringExecutor -> shutter(commandId, systemCommand, shutteringExecutor))
                .collect(toList());
    }

    private ShutteringResult shutter(final UUID commandId, final SystemCommand systemCommand, final ShutteringExecutor shutteringExecutor) {

        logger.info(format("Shuttering %s", shutteringExecutor.getName()));

        return shutteringExecutor.shutter(commandId, systemCommand);
    }
}
