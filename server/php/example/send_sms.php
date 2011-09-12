<?php

// invoke from command line 

require_once __DIR__."/config.php";

$arg_len = sizeof($argv);

if ($arg_len == 4)
{
    $from = $argv[1];
    $to = $argv[2];
    $message = $argv[3];
}
else if ($arg_len == 3)
{    
    $from = $PHONE_NUMBERS[0];
    $to = $argv[1];
    $message = $argv[2];
}
else
{
    error_log("Usage: php send_sms.php [<from>] <to> \"<message>\"");
    error_log("Examples: ");
    error_log("     php send_sms.php 16505551212 16504449876 \"hello world\"");
    error_log("     php send_sms.php 16504449876 \"hello world\"");
    die;
}

$id = uniqid("");

$filename = "$OUTGOING_DIR_NAME/$id.json";

file_put_contents($filename, json_encode(array(
    'from' => $from,
    'to' => $to,
    'message' => $message,
    'id' => $id
)));

echo "Message $id added to outgoing queue\n";