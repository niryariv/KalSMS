<?php

/*
 * Command line script to send an outgoing SMS from the server.
 *
 * Requires an AMQP server to be configured in config.php, and 
 * pushes SMS to the phone immediately using the real-time connection.
 */

require_once __DIR__."/config.php";
require_once dirname(__DIR__)."/EnvayaSMS.php";
require_once __DIR__."/php-amqplib/amqp.inc";

if (sizeof($argv) == 3)
{    
    $to = $argv[1];
    $body = $argv[2];
}
else
{
    error_log("Usage: php send_sms_amqp.php <to> \"<message>\"");
    die;
}

$message = new EnvayaSMS_OutgoingMessage();
$message->id = uniqid("");
$message->to = $to;
$message->message = $body;

$conn = new AMQPConnection($AMQP_SETTINGS['host'], $AMQP_SETTINGS['port'], 
    $AMQP_SETTINGS['user'], $AMQP_SETTINGS['password'], $AMQP_SETTINGS['vhost']);

$ch = $conn->channel();
$ch->queue_declare($AMQP_SETTINGS['queue_name'], false, true, false, false);

$event = new EnvayaSMS_Event_Send(array($message));

$msg = new AMQPMessage($event->render(), array('content_type' => 'application/json', 'delivery-mode' => 2));

$ch->basic_publish($msg, '', $AMQP_SETTINGS['queue_name']);

$ch->close();
$conn->close();

echo "Message {$message->id} added to AMQP queue\n";
