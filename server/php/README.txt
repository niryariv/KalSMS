PHP server library for EnvayaSMS, with example implementation

The EnvayaSMS.php library is intended to be used as part of a PHP application 
running on an HTTP server that receives incoming SMS messages from, and sends 
outgoing SMS messages to, an Android phone running EnvayaSMS.

To run the example implementation, the example/www/ directory should be made available 
via a web server running PHP (e.g. Apache). You can also use the included standalone 
PHP web server, by running the following commands:
    git submodule init
    php server.php
    
example/config.php contains the list of phone numbers and passwords for phones running EnvayaSMS.

On a phone running EnvayaSMS, go to Menu -> Settings and enter:
    * Server URL: The URL to example/www/index.php. 
        If you're using server.php, this will be http://<your_ip_address>:8002/
    * Your phone number: One of the phone numbers listed in example/config.php
    * Password: The corresponding password in example/config.php

To send an outgoing SMS, use 
    php example/send_sms.php

See EnvayaSMS.php and example/www/index.php 