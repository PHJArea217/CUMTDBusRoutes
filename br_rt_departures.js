var br_rt_departures_obj_export = new (function () {
	/* routeList = routeData; startTrip = String or null;
	blockList = blocks[block]; permitRoutes = Array(Number) or null;
	populateRouteData = function (URL, callback(resultJson));
	callback = function(Array({stop=String, arrival=Number, departure=Number} or {trip_id=String, headsign=String, route_id=[Route object], direction=boolean})) */
	this.followBlock = (routeList, startTrip, blockList, permitRoutes, populateRouteData, callback) => {
		var routeSequence = Array();
		var threshold = permitRoutes === null;
		for (var i = 0; i < blockList.length; i++) {
			var r = String(blockList[i]);
			if (r.startsWith('!')) {
				var key = r.substring(1);
				for (var id = 0; id < routeList.length; id++) {
					if (routeList[id].i === key) {
						if (permitRoutes instanceof Array && permitRoutes.indexOf(id) !== -1) threshold = true;
						routeSequence.push(id);
						break;
					}
				}
			}
		}
		if (threshold === false) {
			callback([]);
			return;
		}
		function parse_route_sequence() {
			var routeSequenceIndex = 0;
			var currentRoute = null;
			var result = Array();
			var baseTime = 32767;
			var tripSelected = startTrip === null;
			for (var i = 0; i < blockList.length; i++) {
				var r = String(blockList[i]);
				if (r.startsWith('!')) {
					currentRoute = routeList[routeSequence[routeSequenceIndex++]];
				} else {
					var plusIndex = r.indexOf('+');
					if (plusIndex === -1) continue;
					var arrayHint = Number(r.substring(0, plusIndex));
					var tripId = r.substring(plusIndex + 1);
					if (tripSelected || startTrip === tripId) {
						tripSelected = true;
					} else {
						continue;
					}
					if (!(arrayHint >= 0)) continue;
					var tripGroup = currentRoute.tripInfo[arrayHint];
					var deltaAndService = tripGroup.d[tripId];
					var delta = deltaAndService.d;
					result.push({
						type: 0,
						trip_id: tripId,
						headsign: String(tripGroup.h),
						route_id: currentRoute,
						direction: tripGroup.x,
					});
					for (var n in tripGroup.s) {
						var stopObj1 = tripGroup.s[n];
						result.push({type: 1, stop: stopObj1.n, arrival: stopObj1.a + delta, departure: stopObj1.d + delta});
					}
				}
			}
			callback(result);
		}
		function recursive_populate_route_data(i) {
			if (i >= routeSequence.length) {
				parse_route_sequence();
				return;
			}
			var currentRoute = routeList[routeSequence[i]];
			if (currentRoute.tripInfo === null || currentRoute.tripInfo == undefined) {
				populateRouteData(currentRoute._, (resultJson) => {
					currentRoute.tripInfo = resultJson;
					recursive_populate_route_data(i + 1);
				});
			} else {
				recursive_populate_route_data(i + 1);
			}
		}
		recursive_populate_route_data(0);
	};
	this.meta = null;
	this.allBlocks = Object();
	this.retrieveBlock = (block_id, populateBlockData, callback) => {
		var currentBlock = this.allBlocks["b-" + block_id];
		if (currentBlock == undefined || currentBlock === null) {
			var lastRef = this.meta.tripBlockLimits;
			if (!(lastRef instanceof Array)) return;
			var index = -1;
			for (var i = 0; i < lastRef.length; i++) {
				if (lastRef[i] >= block_id) {
					index = i;
					break;
				}
			}
			if (index === -1) return;
			populateBlockData(`trip-blocks/${i}.json`, (responseJson) => {
				if (!(responseJson instanceof Object)) return;
				for (var b0 in responseJson) {
					this.allBlocks["b-" + b0] = responseJson[b0];
					if (b0 === block_id) {
						callback(responseJson[b0]);
					}
				}
			});
			return;
		}
		callback(currentBlock);
	};
	/* return -> Array({route: {}, headsign: String, direction: boolean, arrivalDelay: Number, addTime: String}) */
	this.getDeparturesByVehicleAtStop = (routeList, stopId, stopObj, vehicle_info, populateRouteData, populateBlockData, callback) => {
		var blockId = vehicle_info.trip.block_id;
		var tripId = vehicle_info.trip.trip_id;
		var currentVehicleStop = vehicle_info.previous_stop_id || vehicle_info.next_stop_id;
		var permitRoutes = stopObj.r;
		
		var currentTripData = null;
		var vehiclePositionTime = Infinity;
		var numberOfStops = -1;
		var result = Array();
		var nextThing = (sequence) => {
			for (var i in sequence) {
				var obj = sequence[i];
				if (obj.type === 0) {
					currentTripData = obj;
				} else if (obj.type === 1) {
					if (vehiclePositionTime === Infinity && currentVehicleStop === obj.stop) {
						vehiclePositionTime = obj.arrival;
						numberOfStops = 0;
					}
					if (vehiclePositionTime !== Infinity) { 
						if (obj.stop === stopId) {
							result.push({
								route: currentTripData.route_id,
								headsign: currentTripData.headsign,
								direction: currentTripData.direction,
								arrivalDelay: obj.arrival - vehiclePositionTime,
								addTime: vehicle_info.last_updated,
								vehicleId: vehicle_info.vehicle_id,
								currentStop: currentVehicleStop,
								numberOfStops: numberOfStops
							});
						}
						numberOfStops++;
					}
				}
			}
			callback(result);
		}
		this.retrieveBlock(blockId, populateBlockData, (block) => {
			this.followBlock(routeList, tripId, block, permitRoutes, populateRouteData, nextThing);
		});
	};
	this.getAllDeparturesAtStop = (routeList, stopId, stopObj, all_vehicles_array,
		populateRouteData, populateBlockData, callback) => {
		if (!(all_vehicles_array instanceof Array)) return;
		var result = Array();
		function recursiveCall(i, o) {
			if (i >= all_vehicles_array.length) {
				callback(result);
				return;
			}
			o.getDeparturesByVehicleAtStop(routeList, stopId, stopObj, all_vehicles_array[i],
				populateRouteData, populateBlockData, (r) => {
				for (var x in r) {
					result.push(r[x]);
				}
				recursiveCall(i + 1, o);
			});
		}
		recursiveCall(0, this);
	};
	this.createDepartureTable = (routeList, stopId, stopData, all_vehicles_array,
		limit, populateRouteData, populateBlockData, tableElem) => {
		var x = this;
		this.getAllDeparturesAtStop(routeList, stopId, stopData[stopId],
			all_vehicles_array, populateRouteData, populateBlockData, (r) => {
			var alternateColor = true;
			r.sort((a, b) => a.arrivalDelay - b.arrivalDelay);
			tableElem.innerHTML = '';
			var hasItem = false;
			for (var row in r) {
				var e = r[row];
				if (e.arrivalDelay > limit) break;
				var trElement = document.createElement('tr');

				/* Color element */
				var routeColorElem = document.createElement('td');
				routeColorElem.style.minWidth = '16px';
				routeColorElem.style.textAlign = 'center';
				if (e.route.b != undefined) {
					routeColorElem.style.backgroundColor = '#' + e.route.b;
					routeColorElem.style.color = '#' + e.route.f;
				}
				var directionText = '';
				if (x.determineDirection != undefined && x.determineDirection !== null) {
					directionText = ' ' + x.determineDirection(e.route, e.direction);
				}
				routeColorElem.innerText = e.route.s + directionText;
				trElement.insertBefore(routeColorElem, null);

				/* Other elements */
				var columns = [e.route.l, e.vehicleId, e.headsign,
					`${e.numberOfStops} stops / ${Math.floor(e.arrivalDelay / 30)} min`,
					stopData[e.currentStop].n];
				for (var i in columns) {
					var el = document.createElement('td');
					el.innerText = columns[i];
					trElement.insertBefore(el, null);
				}

				/* Row color */
				alternateColor = !alternateColor;
				trElement.style.backgroundColor = alternateColor ? '#f8fff8' : '#d8d8d8';

				tableElem.insertBefore(trElement, null);
				hasItem = true;
			}
			if (!hasItem)
				tableElem.innerText = `No departures for the next ${limit / 30} minutes.`;
		});
	};
	this.displayLocationOnTripTable = function(vehicle_info) {
		if (vehicle_info == undefined || vehicle_info === null) return;
		var allTrips = document.getElementsByClassName('trip-table');
		if (allTrips.length === 0) return;
		var tripsById = new Map();
		for (var v = 0; v < allTrips.length; v++) {
			tripsById.set(allTrips[v].getAttribute('_x_tripid'), allTrips[v]);
		}
		var allVehicles = vehicle_info;
		for (var i = 0; i < allVehicles.length; i++) {
			var relevantTripTable = tripsById.get(allVehicles[i].trip.trip_id);
			if (relevantTripTable == undefined) continue;
			var relevantStop = allVehicles[i].previous_stop_id || allVehicles[i].next_stop_id;
			var rows = relevantTripTable.children;
			for (var j = 0; j < rows.length; j++) {
				var stopTd = rows[j].children[0];
				if (stopTd == undefined || stopTd === null) continue;
				if (stopTd.getAttribute('_x_stop_name') === relevantStop)
					stopTd.style.backgroundColor = '#ffc0c0';
				else
					stopTd.style.backgroundColor = '#f8fff8';
			}
		}
	}
})();
