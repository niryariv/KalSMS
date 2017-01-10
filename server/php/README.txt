PHP server library for EnvayaSMS, with example implementation

The EnvayaSMS.php library is intended to be used as part of a PHP application 
running on an HTTP server that receives incoming SMS messages from, and sends 
outgoing SMS messages to, an Android phone running EnvayaSMS.

To run the example implementation, the example/www/ directory should be made available 
via a web server running PHP (e.g. Apache). You can also use the included standalone 
PHP web server, by running the following commands:
    git submodule init
    php server.php
    
example/config.php contains the password for a phone running EnvayaSMS. The password
This password must match the password in the EnvayaSMS app settings,
otherwise example/gateway.php will return an "Invalid password" error.

On a phone running EnvayaSMS, go to Menu -> Settings and enter:
    * Server URL: The URL to example/www/gateway.php. 
        If you're using server.php, this will be http://<your_ip_address>:8002/gateway.php
    * Your phone number: The phone number of your Android phone
    * Password: The password in example/config.php

To send an outgoing SMS, use 
    php example/send_sms.php   

example/www/test.html allows you to simulate the HTTP requests made by EnvayaSMS 
in your browser without actually using the EnvayaSMS app.
If you're using server.php, just go to http://<your_ip_address>:8002/test.html

See EnvayaSMS.php and example/www/gateway.php 
