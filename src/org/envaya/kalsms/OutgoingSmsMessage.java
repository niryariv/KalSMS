
package org.envaya.kalsms;

public class OutgoingSmsMessage  {
    
    private String serverId;    
    private String message;
    private String from;
    private String to;     
    
    private String localId;
        
    private static int nextLocalId = 1;
        
    public OutgoingSmsMessage()
    {
        this.localId = "_o" + getNextLocalId();
    }
    
    static synchronized int getNextLocalId()
    {
        return nextLocalId++;
    }
    
    public String getId()
    {
        return (serverId == null) ? localId : serverId;
    }
    
    public String getLogName()
    {
        return (serverId == null) ? "SMS reply" : ("SMS id=" + serverId);
    }
    
    public String getServerId()
    {
        return serverId;
    }
    
    public void setServerId(String id)
    {
        this.serverId = id;
    }    
           
    public String getMessage()
    {
        return message;
    }
    
    public void setMessage(String message)
    {
        this.message = message;
    }
    
    public String getFrom()
    {
        return from;
    }
    
    public void setFrom(String from)
    {
        this.from = from;
    }
    
    public String getTo()
    {
        return to;
    }
    
    public void setTo(String to)
    {
        this.to = to;
    }
    
}
