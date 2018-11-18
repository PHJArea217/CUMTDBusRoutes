# \(CU)MTD Bus Routes

Interactive version: https://busroutes.peterjin.org/

Non-interactive version: https://busroutes.peterjin.org/about.html

This is a Java application plus a JavaScript web app that provides information
on the various bus routes in the Champaign-Urbana area. It is largely based on
MTD's [GTFS feed](https://developer.cumtd.com/).

The Java application \(```BusRoutes.java```) parses each of the text files
provided in the GTFS feed. Although most of the stucture and parsing is heavily
MTD specific, it could probably be reasonably adapted to other mass transit
systems in other places. For example, each text file is effectively a CSV file,
but quotes are not yet supported because they were not present in the original
files from MTD.

The Java application performs several main tasks:

* Generate static time tables for each route.
* Generate JSON files for each route, trip, and stop.
* Create objects from each route, trip, and stop as part of object-oriented
programming.

The web application \(```bus_routes.html``` including its CSS and JavaScript
files) provides interactive information about these routes from the JSON files
generated from the Java application.

# License

The code base is licensed under the Apache License, version 2.0. No information
about any transit system is present in any file except as to provide additional
information that was not present in the GTFS feed.

MTD is a registered \(?) trademark of the Champaign-Urbana Mass Transit
District.