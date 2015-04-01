package com.skplanet.cisw.gwt.client;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.TextBox;

class ValidatedTextBox extends TextBox implements BlurHandler
{

    private String regexp;

    public ValidatedTextBox()
    {
    }

    public void setValidationRegexp(final String regexp)
    {
        if (this.regexp == null)
        { // First call to this method.
            super.addBlurHandler(this);
        }
        this.regexp = regexp;
    }

    public String getValidationRegexp()
    {
        return regexp;
    }

    public void onBlur(final BlurEvent event)
    {
        final String interval = getText();
        if (!interval.matches(regexp))
        {
            // Steal the dateBoxFormatError :)
            addStyleName("dateBoxFormatError");
            event.stopPropagation();
            DeferredCommand.addCommand(new Command()
            {
                public void execute()
                {
                    // TODO(tsuna): Understand why this doesn't work as
                    // expected, even
                    // though we cancel the onBlur event and we put the focus
                    // afterwards
                    // using a deferred command.
                    // setFocus(true);
                    selectAll();
                }
            });
        }
        else
        {
            removeStyleName("dateBoxFormatError");
        }
    }

}
