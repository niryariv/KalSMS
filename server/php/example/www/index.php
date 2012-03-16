<?php

require_once dirname(__DIR__)."/config.php";
require_once dirname(dirname(__DIR__))."/EnvayaSMS.php";

ini_set('display_errors','0');

// this example implementation uses the filesystem to store outgoing SMS messages,
// but presumably a production implementation would use another storage method

$request = EnvayaSMS::get_request();

$phone_number = $request->phone_number;

$password = @$PASSWORDS[$phone_number];

header("Content-Type: text/xml");

if (!isset($password) || !$request->is_validated($password))
{
    header("HTTP/1.1 403 Forbidden");
    error_log("Invalid request signature");    
    echo EnvayaSMS::get_error_xml("Invalid request signature");
    return;
}

// append to EnvayaSMS app log
$app_log = $request->log;
if ($app_log)
{
    $log_file = dirname(__DIR__)."/log/sms_".preg_replace('#[^\w]#', '', $request->phone_number).".log";        
    $f = fopen($log_file, "a");
    fwrite($f, $app_log);
    fclose($f);        
} 

$action = $request->get_action();

switch ($action->type)
{
    case EnvayaSMS::ACTION_INCOMING:    
        $type = strtoupper($action->message_type);
    
        error_log("Received $type from {$action->from}");
        error_log(" message: {$action->message}");

        if ($action->message_type == EnvayaSMS::MESSAGE_TYPE_MMS)
        {
            foreach ($action->mms_parts as $mms_part)
            {                
                $ext_map = array('image/jpeg' => 'jpg', 'image/gif' => 'gif', 'text/plain' => 'txt', 'application/smil' => 'smil');
                $ext = @$ext_map[$mms_part->type] ?: "unk";
                
                $filename = "mms_parts/" . uniqid('mms') . ".$ext";
                
                copy($mms_part->tmp_name, dirname(__DIR__)."/$filename");
                error_log(" mms part type {$mms_part->type} saved to {$filename}");
            }
        }                       
        
        $reply = new EnvayaSMS_OutgoingMessage();
        $reply->message = "You said: {$action->message}";
    
        error_log("Sending reply: {$reply->message}");
    
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
        
        echo $action->get_response_xml($messages);
        return;
        
    case EnvayaSMS::ACTION_SEND_STATUS:
    
        $id = $action->id;
        
        // delete file with matching id    
        if (preg_match('#^\w+$#', $id) && unlink("$OUTGOING_DIR_NAME/$id.json"))
        {
            echo EnvayaSMS::get_success_xml();
        }       
        else
        {
            header("HTTP/1.1 404 Not Found");
            echo EnvayaSMS::get_error_xml("Invalid id");
        }   
        return;
    case EnvayaSMS::ACTION_DEVICE_STATUS:
        error_log("device_status = {$action->status}");
        echo EnvayaSMS::get_success_xml();
        return;        
    case EnvayaSMS::ACTION_TEST:
        echo EnvayaSMS::get_success_xml();
        return;        
    default:
        header("HTTP/1.1 404 Not Found");
        echo EnvayaSMS::get_error_xml("Invalid action");
        return;
}