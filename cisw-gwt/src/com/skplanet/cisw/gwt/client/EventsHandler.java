package com.skplanet.cisw.gwt.client;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;

/**
 * Handler for multiple events that indicate that the widget may have changed.
 * <p>
 * This handler is just a convenient 4-in-1 handler that can be re-used on a
 * wide range of widgets such as {@code TextBox} and its derivative,
 * {@code CheckBox}, {@code ListBox} etc.
 */
abstract class EventsHandler implements BlurHandler, ChangeHandler,
        ClickHandler, KeyPressHandler
{

    /**
     * Called <em>after</em> one of the events (click, blur, change or "enter"
     * is pressed) occurs.
     * <p>
     * This method is NOT called while the event is happening. It's invoked via
     * a {@link DeferredCommand deferred command}. This entails that the event
     * can't be cancelled as it has already executed. The reason the call is
     * deferred is that this way, things like {@code SuggestBox}s will have
     * already done their auto-completion by the time this method is called, and
     * thus the handler will see the suggested text instead of the partial input
     * being typed by the user.
     * 
     * @param event
     *            The event that occurred.
     */
    protected abstract <H extends EventHandler> void onEvent(DomEvent<H> event);

    public final void onClick(final ClickEvent event)
    {
        scheduleEvent(event);
    }

    // Blur handler get called when widget loses keyboard focus
    public final void onBlur(final BlurEvent event)
    {
        scheduleEvent(event);
    }

    public final void onChange(final ChangeEvent event)
    {
        scheduleEvent(event);
    }

    public final void onKeyPress(final KeyPressEvent event)
    {
        if (event.getCharCode() == KeyCodes.KEY_ENTER)
        {
            scheduleEvent(event);
        }
    }

    /** Executes the event using a deferred command. */
    private <H extends EventHandler> void scheduleEvent(final DomEvent<H> event)
    {
        DeferredCommand.addCommand(new Command()
        {
            public void execute()
            {
                onEvent(event);
            }
        });
    }

}
