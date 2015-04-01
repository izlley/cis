package com.skplanet.cisw.comm;

import java.util.Date;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

final class MailSender
{
    private static final String mGmailSMTP = "smtp.gmail.com";
    private static final String mID              = "ljykjh@gmail.com";
    private static final String mPass          = "rlawhdgk09";
    
    public static void sendEmail( String aFrom,
                                     String aTo,
                                     String aSubj,
                                     String aContent )
    {
        Properties sProp = new Properties();
        sProp.put( "mail.smtp.starttls.enable", "true" );
        sProp.put( "mail.smtp.host", mGmailSMTP );
        sProp.put( "mail.smtp.auth", "true" );
        
        Session sSession = Session.getInstance( sProp,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication( mID, mPass );
                    }
        });
        
        try
        {
            Message sMsg = new MimeMessage(sSession);
            sMsg.setFrom(new InternetAddress( aFrom ));
            sMsg.setRecipients( Message.RecipientType.TO,
                                         InternetAddress.parse(aTo));
            sMsg.setSubject(aSubj);
            sMsg.setContent(aContent, "text/html; charset=EUC-KR");
            sMsg.setSentDate(new Date());
            Transport.send(sMsg);
        }
        catch( MessagingException e )
        {
            throw new RuntimeException(e);
        }
    }
}
