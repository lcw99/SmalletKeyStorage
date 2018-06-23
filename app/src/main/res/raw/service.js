/* Hello, World! Micro Service */

// A micro service will exit when it has nothing left to do.  So to
// avoid a premature exit, let's set an indefinite timer.  When we
// exit() later, the timer will get invalidated.
setInterval(function() {}, 1000)

// Listen for a request from the host for the 'ping' event
LiquidCore.on( 'ping', function() {
    // When we get the ping from the host, respond with "Hallo, die Weld!"
    // and then exit.
    LiquidCore.emit( 'pong', { message: 'Hallo, die Weld!' } )
    process.exit(0)
})

// Ok, we are all set up.  Let the host know we are ready to talk
LiquidCore.emit( 'ready' )