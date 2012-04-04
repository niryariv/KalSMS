<?php

/*
 * An example implementation of the EnvayaSMS server API that uses AMQP 
 * to send outgoing messages in real-time, intended to be used together with
 * example/send_sms_amqp.php.
 *
 * To use this file, set the URL to this file as as the the Server URL in the EnvayaSMS app.
 * The password in the EnvayaSMS app settings must be the same as $PASSWORD in config.php. 
 */

require_once dirname(__DIR__)."/config.php";
require_once dirname(dirname(__DIR__))."/EnvayaSMS.php";

$request = EnvayaSMS::get_request();

header("Content-Type: {$request->get_response_type()}");

if (!$request->is_validated($PASSWORD))
{
    header("HTTP/1.1 403 Forbidden");
    error_log("Invalid password");
    echo $request->render_error_response("Invalid password");
    return;
}

$action = $request->get_action();

switch ($action->type)
{
    case EnvayaSMS::ACTION_INCOMING:    

        // Doesn't do anything with incoming messages
    
        error_log("Received {$action->message_type} from {$action->from}: {$action->message}");       
        echo $request->render_response();
        return;
        
    case EnvayaSMS::ACTION_OUTGOING:
        
        // Doesn't need to do anything when polling for outgoing messages
        // since they should be sent via the AMQP connection.
        
        // Optionally, you could use AMQP basic_get to retrieve any messages
        // from the AMQP queue so that it works in both polling and push modes.
        
        error_log("No messages here, use AMQP instead");        
        echo $request->render_response(array(
            new EnvayaSMS_Event_Log("No messages via polling, use AMQP instead")
        ));
        return;

    case EnvayaSMS::ACTION_AMQP_STARTED:
    
        // The main point of this action is to allow the server to kick off old
        // AMQP connections (that weren't closed properly) before their heartbeat timeout
        // expires. This makes it possible to use long heartbeat timeouts to maximize
        // the phone's battery life.
        
        // With RabbitMQ, this can be done using the management API:
        
        // GET /queues/VHOST/QUEUE_NAME
        //  to get the connection name for each consumer other than the current one
        
        // DELETE /connections/CONNECTION_NAME
        //  to close the connection for each consumer other than the current one
        
        error_log("AMQP connection started with consumer tag {$action->consumer_tag}");  
        echo $request->render_response();
        return;                        
                
    case EnvayaSMS::ACTION_SEND_STATUS:    
        error_log("message {$action->id} status: {$action->status}");       
        echo $request->render_response();                
        return;        
    case EnvayaSMS::ACTION_DEVICE_STATUS:
        error_log("device_status = {$action->status}");
        echo $request->render_response();
        return;                 
    case EnvayaSMS::ACTION_TEST:
        echo $request->render_response();
        return;
    default:
        header("HTTP/1.1 404 Not Found");
        echo $request->render_error_response("The server does not support the requested action.");
        return;
}