<?php

/*
 * Command line script to send an outgoing SMS from the server.
 *
 * This example script queues outgoing messages using the local filesystem.
 * The messages are sent the next time EnvayaSMS sends an ACTION_OUTGOING request to www/gateway.php.
 */

require_once __DIR__."/config.php";
require_once dirname(__DIR__)."/EnvayaSMS.php";

if (sizeof($argv) == 3)
{    
    $to = $argv[1];
    $body = $argv[2];
}
else
{
    error_log("Usage: php send_sms.php <to> \"<message>\"");
    error_log("Example: ");
    error_log("     php send_sms.php 16504449876 \"hello world\"");
    die;
}

$message = new EnvayaSMS_OutgoingMessage();
$message->id = uniqid("");
$message->to = $to;
$message->message = $body;

file_put_contents("$OUTGOING_DIR_NAME/{$message->id}.json", json_encode($message));
    
echo "Message {$message->id} added to filesystem queue\n";
