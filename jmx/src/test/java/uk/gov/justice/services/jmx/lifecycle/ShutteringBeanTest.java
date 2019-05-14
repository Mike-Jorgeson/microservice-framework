package uk.gov.justice.services.jmx.lifecycle;

import static java.util.stream.Stream.of;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.lifecycle.events.shuttering.ShutteringCompleteEvent;
import uk.gov.justice.services.core.lifecycle.events.shuttering.UnshutteringCompleteEvent;
import uk.gov.justice.services.messaging.jms.EnvelopeSenderSelector;
import uk.gov.justice.services.messaging.jms.ShutteredCommandSender;
import uk.gov.justice.services.shuttering.domain.ShutteredCommand;
import uk.gov.justice.services.shuttering.persistence.ShutteringPersistenceException;
import uk.gov.justice.services.shuttering.persistence.ShutteringRepository;

import java.time.ZonedDateTime;

import javax.enterprise.event.Event;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ShutteringBeanTest {

    @Mock
    private EnvelopeSenderSelector envelopeSenderSelector;

    @Mock
    private ShutteringRepository shutteringRepository;

    @Mock
    private ShutteredCommandSender shutteredCommandSender;

    @Mock
    private Event<ShutteringCompleteEvent> shutteringCompleteEventFirer;

    @Mock
    private Event<UnshutteringCompleteEvent> unshutteringCompleteEventFirer;

    @Mock
    private UtcClock clock;

    @InjectMocks
    private ShutteringBean shutteringBean;

    @Captor
    private ArgumentCaptor<ShutteringCompleteEvent> shutteringCompleteEventCaptor;

    @Captor
    private ArgumentCaptor<UnshutteringCompleteEvent> unshutteringCompleteEventCaptor;

    @Test
    public void shouldSetShutteredToTrue() throws Exception {

        final ZonedDateTime now = new UtcClock().now();

        when(clock.now()).thenReturn(now);

        shutteringBean.shutter();

        verify(envelopeSenderSelector).setShuttered(true);
        verify(shutteringCompleteEventFirer).fire(shutteringCompleteEventCaptor.capture());

        final ShutteringCompleteEvent shutteringCompleteEvent = shutteringCompleteEventCaptor.getValue();

        assertThat(shutteringCompleteEvent.getShutteringCompleteAt(), is(now));
    }

    @Test
    public void shouldDrainTheShutteringCommandQueueAndSetShutteredToFalse() throws Exception {

        final ShutteredCommand shutteredCommand_1 = mock(ShutteredCommand.class);
        final ShutteredCommand shutteredCommand_2 = mock(ShutteredCommand.class);

        final ZonedDateTime now = new UtcClock().now();

        when(shutteringRepository.streamShutteredCommands()).thenReturn(of(shutteredCommand_1, shutteredCommand_2));
        when(clock.now()).thenReturn(now);

        shutteringBean.unshutter();

        final InOrder inOrder = inOrder(shutteredCommandSender, envelopeSenderSelector, unshutteringCompleteEventFirer);

        inOrder.verify(shutteredCommandSender).sendAndDelete(shutteredCommand_1);
        inOrder.verify(shutteredCommandSender).sendAndDelete(shutteredCommand_2);
        inOrder.verify(envelopeSenderSelector).setShuttered(false);
        inOrder.verify(unshutteringCompleteEventFirer).fire(unshutteringCompleteEventCaptor.capture());

        final UnshutteringCompleteEvent unshutteringCompleteEvent = unshutteringCompleteEventCaptor.getValue();

        assertThat(unshutteringCompleteEvent.getUnshutteringCompletedAt(), is(now));
    }

    @Test
    public void shouldNotUnshutterIfDrainingTheCommandQueueFails() throws Exception {

        final ShutteringPersistenceException shutteringPersistenceException = new ShutteringPersistenceException("Ooops");

        final ShutteredCommand shutteredCommand_1 = mock(ShutteredCommand.class);
        final ShutteredCommand shutteredCommand_2 = mock(ShutteredCommand.class);

        when(shutteringRepository.streamShutteredCommands()).thenReturn(of(shutteredCommand_1, shutteredCommand_2));
        doThrow(shutteringPersistenceException).when(shutteredCommandSender).sendAndDelete(shutteredCommand_2);

        try {
            shutteringBean.unshutter();
            fail();
        } catch (final ShutteringPersistenceException expected) {
            assertThat(expected, is(shutteringPersistenceException));
        }

        verify(shutteredCommandSender).sendAndDelete(shutteredCommand_1);

        verifyZeroInteractions(envelopeSenderSelector);
        verifyZeroInteractions(unshutteringCompleteEventFirer);
    }
}
