<?php

require_once dirname(__DIR__)."/config.php";
require_once dirname(dirname(__DIR__))."/EnvayaSMS.php";

ini_set('display_errors','0');

// this example implementation uses the filesystem to store outgoing SMS messages,
// but presumably a production implementation would use another storage method

$request = EnvayaSMS::get_request();

$phone_number = $request->phone_number;

$password = @$PASSWORDS[$phone_number];

if (!isset($password) || !$request->is_validated($password))
{
    header("HTTP/1.1 403 Forbidden");
    error_log("Invalid request signature");
    echo "Invalid request signature";
    return;
}

$action = $request->get_action();

switch ($action->type)
{
    case EnvayaSMS::ACTION_INCOMING:    
        error_log("Received SMS from {$action->from}");
        
        $reply = new EnvayaSMS_OutgoingMessage();
        $reply->message = "You said: {$action->message}";
    
        error_log("Sending reply: {$reply->message}");
    
        header("Content-Type: text/xml");
        echo $action->get_response_xml(array($reply));
        return;
        
    case EnvayaSMS::ACTION_OUTGOING:
        $messages = array();
   
        $dir = opendir($OUTGOING_DIR_NAME);
        while ($file = readdir($dir)) 
        {
            if (preg_match('#\.json$#', $file))
            {
                $data = json_decode(file_get_contents("$OUTGOING_DIR_NAME/$file"), true);
                if ($data && @$data['from'] == $phone_number)
                {
                    $sms = new EnvayaSMS_OutgoingMessage();
                    $sms->id = $data['id'];
                    $sms->to = $data['to'];
                    $sms->from = $data['from'];
                    $sms->message = $data['message'];
                    $messages[] = $sms;
                }
            }
        }
        closedir($dir);
        
        header("Content-Type: text/xml");
        echo $action->get_response_xml($messages);
        return;
        
    case EnvayaSMS::ACTION_SEND_STATUS:
    
        $id = $action->id;
        
        // delete file with matching id    
        if (preg_match('#^\w+$#', $id) && unlink("$OUTGOING_DIR_NAME/$id.json"))
        {
            echo "OK";            
        }       
        else
        {
            header("HTTP/1.1 404 Not Found");
            echo "invalid id";            
        }   
        return;
    case EnvayaSMS::ACTION_TEST:
        echo "OK";
        return;        
    default:
        header("HTTP/1.1 404 Not Found");
        echo "Invalid action";
        return;
}