package org.envaya.sms;

import android.content.Intent;
import android.telephony.SmsManager;
import java.util.ArrayList;

public class OutgoingSms extends OutgoingMessage {

    public OutgoingSms(App app)
    {
        super(app);
    }    
    
    public String getLogName()
    {
        return "SMS";
    }

    private ArrayList<String> _bodyParts;
    
    public ArrayList<String> getBodyParts()
    {
        if (_bodyParts == null)
        {
            SmsManager smgr = SmsManager.getDefault();
            _bodyParts = smgr.divideMessage(getMessageBody());
        }
        return _bodyParts;
    }
    
    public int getNumParts()
    {
        return getBodyParts().size();        
    }
    
    public class ScheduleInfo extends OutgoingMessage.ScheduleInfo
    {
        public String packageName;
    }
        
    public OutgoingMessage.ScheduleInfo scheduleSend()
    {        
        ScheduleInfo schedule = new ScheduleInfo();
        
        int numParts = getNumParts();
        String packageName = app.chooseOutgoingSmsPackage(numParts);            

        if (packageName == null)
        {            
            schedule.time = app.getNextValidOutgoingTime(numParts);                
            schedule.now = false;
        }                
        else
        {
            schedule.now = true;
            schedule.packageName = packageName;
        }
        
        return schedule;
    }
    
    public void send(OutgoingMessage.ScheduleInfo _schedule)
    {
        ScheduleInfo schedule = (ScheduleInfo)_schedule;
        
        if (numRetries == 0)
        {
            app.log("Sending " + getDescription());
        }
        else
        {        
            app.log("Retrying sending " + getDescription());
        }
                
        ArrayList<String> bodyParts = getBodyParts();
        int numParts = bodyParts.size();
        if (numParts > 1)
        {
            app.log("(Multipart message with "+numParts+" parts)");
        }        

        Intent intent = new Intent(schedule.packageName + App.OUTGOING_SMS_INTENT_SUFFIX, this.getUri());
        intent.putExtra(App.OUTGOING_SMS_EXTRA_DELIVERY_REPORT, false);
        intent.putExtra(App.OUTGOING_SMS_EXTRA_TO, getTo());
        intent.putExtra(App.OUTGOING_SMS_EXTRA_BODY, bodyParts);
        
        app.sendBroadcast(intent, "android.permission.SEND_SMS");        
    }    
    
    public String getDisplayType()
    {
        return "SMS";
    }

    @Override
    public void validate() throws ValidationException
    {                        
        super.validate();
                
        String to = getTo();
        if (to == null || to.length() == 0)
        {
            throw new ValidationException("Destination address is empty");
        }                        
        
        if (!app.isForwardablePhoneNumber(to))
        {
            // this is mostly to prevent accidentally sending real messages to
            // random people while testing...
            throw new ValidationException("Destination address is not allowed");
        }
        
        String messageBody = getMessageBody();
        
        if (messageBody == null || messageBody.length() == 0)
        {
            throw new ValidationException("Message body is empty");
        }        
        
        int numParts = getNumParts();

        if (numParts > App.OUTGOING_SMS_MAX_COUNT)
        {
            throw new ValidationException("Message has too many parts ("+numParts+")");
        }
    }
}
