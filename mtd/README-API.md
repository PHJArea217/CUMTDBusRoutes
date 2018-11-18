MTD provides an API \(https://developer.cumtd.com/) that provides information
on route, trip, stop, shape, and vehicle information. Much of this is already
present in the GTFS feed, but real-time vehicle and reroute information is not.
However, direct access to the API from the client web browser is not easy for
the following reasons:

* The API requires the use of an API key. As with all API keys, it should be
kept private and not be in client-side code, effectively precluding access from
web browsers.
* Even if the API key were allowed in the client-side code, it has a limit of
1000 requests per hour for each API key. In the event of high-volume web
traffic, it would effectively overload the API.
* The API does not support IPv6.

To mitigate these effects, an App Engine app has been created at
https://webclockbackend.appspot.com/cumtd \(this URL is subject to change) that
effectively "proxies" the results of GetVehicles and GetReroutes to the
application, allowing the use of those APIs without an API key.

There are two ways to use this app:

## Unauthenticated Requests

Unauthenticated requests are simply GET requests on /cumtd on either
webclockbackend.appspot.com or appsvr.peterjin.org that return the appropriate
data.

## Authenticated Requests

Authenticated requests use /cumtd/login on the same domains. However, the
user will be asked for an API key through the basic authentication prompt.

The username is "apikey" and the password is the API key provided by MTD.
You may obtain an API key from https://developer.cumtd.com/ for free.

---

# Caching

Data retrieved from MTD is cached for the following times \(in the memcache):

```
            Unauthenticated Authenticated
getvehicles      45 seconds    10 seconds
getreroutes      10 minutes     5 minutes
getapiusage       5 minutes           N/A
```

Due to the API's rate limits, it is not possible to flush the cache arbitrarily.
However, as the data returned from the API is effectively identical for each
API key, there are no separate caches for authenticated and unauthenticated
requests. This basically means that data is normally retrieved using the
"default" API key, but specifying a different API key will cause the data to
be able to be updated much faster, effectively improving performance for
everyone.

# Usage

## Real-time departure data

To generate real-time departure data for each stop, we get vehicle information
using GetVehicles. Each vehicle has a trip ID and the stops that the vehicle
is currently between. We can cross-reference this with stop information for each
trip provided by the GTFS feed to determine the difference between the
expected clock time for the trip at the bus' current stop versus the actual 
clock time. This gets us the delay of the trip; adding this to the expected
arrival time for the actual destination stop, we get the actual arrival time at
the stop.
