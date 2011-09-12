<?php

/*
 * PHP server library for KalSMS
 *
 * For example usage see example/www/index.php
 */

class KalSMS
{
    const ACTION_INCOMING = 'incoming';
    const ACTION_OUTGOING = 'outgoing';
    const ACTION_SEND_STATUS = 'send_status';
    const ACTION_TEST = 'test';

    const STATUS_QUEUED = 1;
    const STATUS_FAILED = 2;
    const STATUS_SENT = 3;
    
    static function new_from_request()
    {
        $version = @$_SERVER['HTTP_X_KALSMS_VERSION'];        
    
        return new KalSMS();
    }    
    
    static function escape($val)
    {
        return htmlspecialchars($val, ENT_QUOTES, 'UTF-8');
    }
    
    function get_request_action()
    {
        switch (@$_POST['action'])
        {
            case static::ACTION_INCOMING:
                return new KalSMS_Action_Incoming($this);
            case static::ACTION_OUTGOING:
                return new KalSMS_Action_Outgoing($this);                
            case static::ACTION_SEND_STATUS:
                return new KalSMS_Action_SendStatus($this);
            case static::ACTION_TEST:
                return new KalSMS_Action_Test($this);
            default:
                return new KalSMS_Action($this);
        }
    }        
    
    function get_request_phone_number()
    {
        return @$_SERVER['HTTP_X_KALSMS_PHONENUMBER'];
    }        
    
    function is_validated_request($correct_password)
    {
        $signature = @$_SERVER['HTTP_X_KALSMS_SIGNATURE'];        
        if (!$signature)
        {
            return false;
        }
        
        $is_secure = (!empty($_SERVER['HTTPS']) AND filter_var($_SERVER['HTTPS'], FILTER_VALIDATE_BOOLEAN));
        $protocol = $is_secure ? 'https' : 'http';
        $full_url = $protocol . "://" . $_SERVER['HTTP_HOST'] . $_SERVER['REQUEST_URI'];    
        
        $correct_signature = $this->compute_signature($full_url, $_POST, $correct_password);           
        
        //error_log("Correct signature: '$correct_signature'");
        
        return $signature === $correct_signature;
    }

    function compute_signature($url, $data, $password)
    {
        ksort($data);
        
        $input = $url;
        foreach($data as $key => $value)
            $input .= ",$key=$value";

        $input .= ",$password";
        
        //error_log("Signed data: '$input'");
        
        return base64_encode(sha1($input, true));            
    }
}

class KalSMS_OutgoingMessage
{
    public $id = '';
    public $to;
    public $message;
}

class KalSMS_Action
{
    public $type;    
    public $kalsms;
    
    function __construct($kalsms)
    {
        $this->kalsms = $kalsms;
    }
}

class KalSMS_Action_Test extends KalSMS_Action
{    
    function __construct($kalsms)
    {
        parent::__construct($kalsms);
        $this->type = KalSMS::ACTION_TEST;
    }    
}

class KalSMS_Action_Incoming extends KalSMS_Action
{    
    public $from;
    public $message;

    function __construct($kalsms)
    {
        parent::__construct($kalsms);
        $this->type = KalSMS::ACTION_INCOMING;
        $this->from = $_POST['from'];
        $this->message = $_POST['message'];
    }
    
    function get_response_xml($messages)
    {
        ob_start();
        echo "<?xml version='1.0' encoding='UTF-8'?>\n";
        echo "<Response>";
        foreach ($messages as $message)
        {   
            echo "<Sms id='".KalSMS::escape($message->id)."'>".KalSMS::escape($message->message)."</Sms>";
        }
        echo "</Response>";        
        return ob_get_clean();
    }
}

class KalSMS_Action_Outgoing extends KalSMS_Action
{    
    function __construct($kalsms)
    {
        parent::__construct($kalsms);
        $this->type = KalSMS::ACTION_OUTGOING;
    }
    
    function get_response_xml($messages)
    {
        ob_start();
        echo "<?xml version='1.0' encoding='UTF-8'?>\n";
        echo "<Messages>";
        foreach ($messages as $message)
        {   
            echo "<Sms id='".KalSMS::escape($message->id)."' to='".KalSMS::escape($message->to)."'>".
                KalSMS::escape($message->message)."</Sms>";
        }
        echo "</Messages>";        
        return ob_get_clean();
    }
}

class KalSMS_Action_SendStatus extends KalSMS_Action
{    
    public $status;
    public $id;

    function __construct($type)
    {
        $this->type = KalSMS::ACTION_SEND_STATUS;        
        $this->status = (int)$_POST['status'];
        $this->id = $_POST['id'];
    } 
}