
Race Time Server

Race Time Server provides an interface between third-party timing software,
such as [LiveTime](https://www.livetimescoring.com/),
and [TBS RaceTracker](https://www.team-blacksheep.com/products/prod:tbs_racetracker).

Race Time Server is currently only available as an Android application
due to the difficulty of trying to get anything meaningful working in Python on a Raspberry Pi
(Bluetooth issues).
It also requires the use of a Node.js server to translate socket.io to web sockets
as I couldn't find any simple socket.io server library for Android.

[Timing software] -- socket.io --> [Node.js server] -- web socket --> [Android] -- Bluetooth --> [RaceTracker]
