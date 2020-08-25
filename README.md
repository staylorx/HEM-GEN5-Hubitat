# HEM-GEN5-Hubitat

Aeon Aeotec Home Energy Meter, Gen5 Hubitat Device Drive

This is a HEM Gen5 driver for the Hubitat hub. I'm having a complicated relationship with this driver, and its many authors. What I need is a simple driver that logs current off the probes. This isn't what comes with the default driver from the hub. Seeing as my devices have two clamps that measure, um... current, it would seem that would be reasonable to expect. Unfortunately, this means custom drivers.

The driver I want is primarily aimed at supporting the syslog capabilities of the Hubitat device, and as a consequence, it doesn't need all the extra logging detail of the original driver. However, the drivers I'm finding all have tons of extra reporting capabilities to turn the hub into a data-logger, right down to the ability to add price/kWh. That's all work I'd rather have done at the supervisory/logging system.

The syslog driver I use and contribute to is here: https://github.com/hubitatuser12/hubitatCode

## Install

Copy and paste the entire contents from the a driver in the `drivers` folder file into a new device driver in Hubitat.

Then change or update the driver for your HEM device.
