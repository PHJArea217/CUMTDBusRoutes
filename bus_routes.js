/*eslint-disable no-unused-vars */
'use strict';
function this_is_the_entire_busroutes_js_file() {

/* Replace if necessary */
var agency_specific_operations = "mtd_ops" in window ? mtd_ops : {};

function load() {
	window.onhashchange = _onhashchange_me;
	_onhashchange_me(null);
}
const ORIGINAL_TITLE = 'MTD Bus Routes';
var isolateTrip = null;
var showStopOnTrip = -1;
function _onhashchange_me(e) {
	var h = String(window.location.hash);
	if (h === '#singleTrip') {
		return;
	}
	isolateTrip = null;
	if (h.search(/^#stop-\d+$/) === 0) {
		initStopData(() => br_display('s', Number(h.substr(6))));
		return;
	} else if (h.search(/^#route-\d+$/) === 0) {
		showStopOnTrip = -1;
		initRouteData(() => br_display('r', Number(h.substr(7))));
		return;
	} else if (h.search(/^#route-\d+\/\d+$/) === 0) {
		var slashP = h.indexOf('/');
		if (slashP === -1) return;
		showStopOnTrip = Number(h.substr(slashP + 1));
		initRouteData(() => br_display('r', Number(h.substr(7, slashP - 7))));
		return;
	}
	if (h === '#routes') br_display('r', null);
	else if (h === '#stops' || (h.search(/^#stoplist-last-.$/) === 0)) {
		if (document.getElementById('stop-list-plain') === null || h === '#stops') br_display('s', null);
	}
	else if (h === '#stopsByDistance') br_display('s', 'by-distance');
	else {
		document.getElementById("main").innerHTML = "Select 'Routes' or 'Stops' from above.<br /><img src=\"img/bus-1.jpg\" />";
		document.title = ORIGINAL_TITLE;
	}
}
var callbackQueue = {};
function fetchFile(name, callback, errorHandler) {
	name = String(name);
	var cacheKey = name.startsWith("http") ? null : "br json cache " + name;
	var xhr = new XMLHttpRequest();
	xhr.open("GET", name);
	xhr.onreadystatechange = function () {
		if (xhr.readyState === 4) {
			if (xhr.status === 200) {
				var rt = xhr.responseText;
				if (cacheKey !== null) sessionStorage.setItem(cacheKey, rt);
				let json = JSON.parse(rt);
				if (cacheKey in callbackQueue) {
					for (let x of callbackQueue[cacheKey]) {
						x(xhr, json);
					}
					callbackQueue[cacheKey] = [];
				} else {
					callback(xhr, json);
				}
			} else {
				if (errorHandler !== null) {
					errorHandler(xhr);
				}
			}
		}
	};
	if (cacheKey !== null) {
		var r = sessionStorage.getItem(cacheKey);
		if (r != undefined && r !== null) {
			callback(null, JSON.parse(r));
			return;
		}
		if (callbackQueue[cacheKey] == undefined) {
			callbackQueue[cacheKey] = [callback];
		} else {
			callbackQueue[cacheKey].push(callback);
			return;
		}
	}
	xhr.send();
}
var routeData = null;
var stopData = null;
var routeSelector = null;
var stopSelector = null;
var routeTitleElem = null;
var currentRoute = null;
var meta = null;
var timerNumber = -1;
function initStopData(callback) {
	if (stopData === null) {
		fetchFile("meta/stops.json", /* @callback */ function(xhr, resp) {
			if (resp === null) return;
			stopData = resp;
			var i = 0;
			for (var stopId in resp) {
				stopData[stopId].number = i;
				i++;
			}
			callback();
		}, null);
	} else {
		callback();
	}
}
function initMeta(callback) {
	initFile(meta, "meta/meta.json", (r) => {
		meta = r;
		if (br_rt_departures_obj_export != undefined) {
			br_rt_departures_obj_export.meta = r;
		}
		callback();
	});
}
var serviceData = null;
function initService(callback) {
	initFile(serviceData, "meta/service.json", (r) => {
		serviceData = r;
		callback();
	});
}
function initFile(orig, filename, callback) {
	if (orig === null) {
		fetchFile(filename, /* @callback */ function(xhr, resp) {
			if (resp === null) return;
			callback(resp);
		}, null);
	} else {
		callback(orig);
	}
}
function initRouteData(callback) {
	if (routeData === null) {
		fetchFile("meta/routes.json", /* @callback */ function(xhr, resp) {
			if (resp === null) return;
			routeData = resp;
			for (var i in routeData) {
				if (routeData[i].l == undefined) {
					routeData[i].l = routeData[i].s;
				}
			}
			callback();
		}, null);
	} else {
		callback();
	}
}
function getTimeNumber(number) {
	if (number == 32767) return ' ';
	var adj = Number(number) + 32768;
	if (Number.isNaN(adj)) return "";
	var h = Math.floor(adj / 1800);
	var m = Math.floor((adj % 1800) / 30);
	var s = adj % 30 * 2;
	var p = m => (m < 10 ? '0' : '') + m;
	return p(h) + ':' + p(m) + (s === 0 ? '' : ':' + p(s));
}
function getTimeNumberFromDate(date) {
	if (date === null) return 32767;
	var h = date.getHours();
	var m = date.getMinutes();
	var s = date.getSeconds();
	return (h * 1800 + m * 30 + s / 2) - 32768;
}
function safeMapAccess(m, i) {
	if (m === null || m == undefined) {
		return null;
	}
	if (!m.hasOwnProperty(i)) return null;
	return m[i];
}
function br_display(t, n) {
	var main = document.getElementById("main");
	if (t === 'r') {
		document.title = "Routes";
		clearTimeout(timerNumber);
		main.innerHTML = "";
		var display_preselected_route = () => {
			if (n === -1) return;
			for (var i in routeSelector.options) {
				var rn = Number(routeSelector.options[i].value);
				if (rn === n) {
					displayRoute(routeData[rn]);
					routeSelector.selectedIndex = i;
					return;
				}
			}
			/*
			let busImageList = ["img/bus-1.jpg"];
			for (let x of busImageList) {
				let busImg = document.createElement("img");
				busImg.src = x;
				busImg.setAttribute("class", "vehicle-image");
				main.insertBefore(busImg, null);
			}
			*/
		};
		if (routeSelector === null) {
			var cb = () => {
				routeSelector = document.createElement('select');
				routeSelector.setAttribute('id', 'route-selector');
				routeSelector.onchange = function() {
					var v = Number(routeSelector.options[routeSelector.selectedIndex].value);
					window.location.hash = '#route-' + v;
				};
				routeSelector_step2(routeSelector, routeData, main);
				display_preselected_route();
			};
			initRouteData(cb);
		} else {
			main.insertBefore(routeSelector, null);
			routeSelector.selectedIndex = 0;
			insert_display_element('route_tables', main);
			display_preselected_route();
		}
	} else if (t === 's') {
		if (n !== null && n != undefined && !(String(n).startsWith('b'))) {
			var sn1 = Number(n);
			if (Number.isSafeInteger(sn1)) {
				br_show_stop(sn1);
			}
			return;
		}
		document.title = "Stops";
		clearTimeout(timerNumber);
		main.innerHTML = '';
		var mycb = function(by_distance) {
			var elements = Array();
			for (var stopId in stopData) {
				var opt = document.createElement('a');
				var stopNumber = Number(stopData[stopId].number);
				if (!Number.isSafeInteger(stopNumber)) {
					stopNumber = -1;
				}
				opt.setAttribute('href', `#stop-${stopNumber}`);
				opt.setAttribute('_x_distance', stopData[stopId].distanceFromHome);
				opt.innerText = String(stopData[stopId].n) + (by_distance ?
					` (${stopData[stopId].distanceFromHome} m)` : '');
				elements.push(opt);
			}
			if (!by_distance)
				elements.sort(_compareRouteName((a) => String(a.innerHTML).toUpperCase()));
			else
				elements.sort(_compareRouteName((a) => Number(a.getAttribute('_x_distance'))));
			var displayList = document.createElement('ul');
			var letterList = document.createElement('div');
			var letterList_text = "Use Ctrl-F to search for stops<br />";
			var lastLetter = '-';
			let currentHash = window.location.hash;
			let displayData = function(i) {
				let doFocus = false;
				if (!by_distance) {
					var letter = elements[i].innerText.charAt(0).toUpperCase();
					if (letter.search(/\w/) === 0 && letter !== lastLetter) {
						lastLetter = letter;
						elements[i].setAttribute('id', 'stoplist-last-' + letter);
						letterList_text += `<a href="#stoplist-last-${letter}">${letter}</a> `;
						letterList.innerHTML = letterList_text;
						if (currentHash === `#stoplist-last-${letter}`)
							doFocus = true;
					}
				}
				var liWrapper = document.createElement('li');
				liWrapper.insertBefore(elements[i], null);
				displayList.insertBefore(liWrapper, null);
				if (doFocus) elements[i].focus();
			};
			for (let i in elements) {
				setTimeout(() => displayData(i), 0);
			}
			var divWrapper = document.createElement('div');
			divWrapper.insertBefore(displayList, null);
			divWrapper.setAttribute('id', 'stop-list-plain');
			main.insertBefore(letterList, null);
			main.insertBefore(divWrapper, null);
		};
		if (n === 'by-distance') {
			initStopData(() => populate_stop_distances(() => mycb(true), true));
		} else {
			initStopData(() => mycb(false));
		}
	}
}
function br_show_stop(number) {
	initRouteData(() => br_show_stop_r2(number));
}
function br_show_stop_r2(number) {
	var data = null;
	var name = null;
	for (var sn in stopData) {
		var d = stopData[sn];
		if (d.number === number) {
			data = d;
			name = sn;
			break;
		}
	}
	if (data === null) return;
	var main = document.getElementById('main');
	clearTimeout(timerNumber);
	main.innerHTML = '';
	document.title = data.n;
	window.location.hash = 'stop-' + number;
	var stopNameDiv = document.createElement('div');
	stopNameDiv.setAttribute('id', 'stop-name-large');
	stopNameDiv.innerText = data.n;
	main.insertBefore(stopNameDiv, null);
	var stopTable = document.createElement('table');
	stopTable.setAttribute('id', 'stop-table');
	stopTable.setAttribute('border', 1);
	stopTable.setAttribute('cellpadding', 3);
	var displayTableEntry = function(key, value) {
		if (key === null || value === null || value == undefined) return;
		var row = document.createElement('tr');
		var keyTd = document.createElement('td');
		var valueTd = document.createElement('td');
		keyTd.innerText = key;
		if (value instanceof Element) {
			valueTd.insertBefore(value, null);
		} else {
			valueTd.innerText = value;
		}
		row.insertBefore(keyTd, null);
		row.insertBefore(valueTd, null);
		stopTable.insertBefore(row, null);
	};
	if (agency_specific_operations.sms_destination_number) {
		let aElem = document.createElement('a');
		aElem.setAttribute('href', 'sms:' + agency_specific_operations.sms_destination_number + '?body=' + data.c);
		aElem.innerText = data.c;
		displayTableEntry("SMS code", aElem);
	} else {
		displayTableEntry("SMS code", data.c);
	}
	displayTableEntry("Information", data.d);
	if (String(data.u).startsWith("http")) {
		var aElem = document.createElement('a');
		aElem.setAttribute('href', data.u);
		aElem.innerText = data.u;
		displayTableEntry("URL", aElem);
	}
	let location_ = [Number(data.y), Number(data.x)];
	if (location_[0] > -90 && location_[1] > -180) {
		var xElem = document.createElement("span");
		let ns = 'N';
		let ew = 'E';
		let mylat = location_[0];
		let mylon = location_[1];
		if (location_[0] < 0) {mylat = -mylat; ns = 'S';};
		if (location_[1] < 0) {mylon = -mylon; ew = 'W';};
		xElem.innerHTML = `${mylat} ${ns}, ${mylon} ${ew}<br /><a href="https://www.openstreetmap.org/?mlat=${location_[0]}&mlon=${location_[1]}&zoom=15">View on OpenStreetMap</a>`
		displayTableEntry("Location", xElem);
	}
	/* nearby stops */
	let nearbyStops = [];
	for (let s in stopData) {
		if (s === name) continue;
		nearbyStops.push(stopData[s]);
	}
	populate_stop_distances(null, false)({coords: {latitude: data.y, longitude: data.x}});
	nearbyStops.sort(_compareRouteName(i => i.distanceFromHome));

	let nearbyStopList = document.createElement('div');
	const hardLimitE = 10;
	for (let i = 0; i < hardLimitE; i++) {
		if (i >= nearbyStops.length) break;
		let divElemOfStop = document.createElement('div');
		let aElemOfStop = document.createElement('a');
		aElemOfStop.innerText = nearbyStops[i].n + ` (${nearbyStops[i].distanceFromHome} m)`;
		aElemOfStop.setAttribute('href', '#stop-' + nearbyStops[i].number);
		divElemOfStop.insertBefore(aElemOfStop, null);
		nearbyStopList.insertBefore(divElemOfStop, null);
	}
	displayTableEntry("Nearby stops", nearbyStopList);

	var routeList = document.createElement('div');
	if (!(data.r instanceof Array)) {
		data.r = [];
	}
	data.r.sort((a, b) => routeData[a].l < routeData[b].l ? -1 :
						routeData[a].l > routeData[b].l ? 1 : 0);
	for (var i in data.r) {
		var divElemOfRoute = document.createElement('div');
		var aElemOfRoute = document.createElement('a');
		var routeNumber = Number(data.r[i]);
		if (!Number.isSafeInteger(routeNumber)) routeNumber = 'null';
		aElemOfRoute.setAttribute('href', `#route-${routeNumber}/${number}`);
		aElemOfRoute.innerText = routeData[data.r[i]].l;
		divElemOfRoute.insertBefore(aElemOfRoute, null);
		routeList.insertBefore(divElemOfRoute, null);
	}
	displayTableEntry('Routes', routeList);
	main.insertBefore(stopTable, null);
	if (!agency_specific_operations) return;
	var departureTable = document.createElement('table');
	var populate = (u, c) => fetchFile(u, (x, r) => c(r), null);
	departureTable.setAttribute('class', 'departure-table');
	departureTable.setAttribute('border', 1);
	var link = document.createElement('a');
	link.addEventListener('click', function(e) {
		initMeta(() => 
			agency_specific_operations.display_rt_on_table(routeData,
				name, stopData, fetchFile, populate, populate, departureTable, 2700)
		)
	}, null);
	link.setAttribute('href', 'javascript:void(0);');
	link.innerText = 'Show real-time departure information...';
	departureTable.insertBefore(link, null);
	main.insertBefore(departureTable, null);
}
function populate_stop_distances(callback, doGeo) {
	/* https://en.wikipedia.org/wiki/Geographic_coordinate_system */
	var wgs84_latPos = (y) => (-0.0023*Math.sin(6*y)/6 + 1.175*Math.sin(4*y)/4 - 559.82*Math.sin(2*y)/2 + 111132.92*y) * 180 / Math.PI;
	var wgs84_lonPos = (x, y) => x * (0.118*Math.cos(5*y) - 93.5*Math.cos(3*y) + 111412.84*Math.cos(y)) * 180 / Math.PI;
	if (!doGeo || "geolocation" in navigator) {
		var lb = (position) => {
			var yAngle = position.coords.latitude / 180 * Math.PI;
			var yPos = wgs84_latPos(yAngle);
			var xPos = wgs84_lonPos(position.coords.longitude / 180 * Math.PI, yAngle);
			if (Number.isNaN(xPos + yPos)) return;
			for (var s in stopData) {
				yAngle = stopData[s].y / 180 * Math.PI;
				var stopY = wgs84_latPos(yAngle);
				var stopX = wgs84_lonPos(stopData[s].x / 180 * Math.PI, yAngle);
				if (Number.isNaN(stopX + stopY)) continue;
				stopData[s].distanceFromHome = Math.round(Math.hypot(yPos - stopY, xPos - stopX));
			}
			if (callback) callback(stopData);
		};
		if (doGeo)
			navigator.geolocation.getCurrentPosition(lb);
		else
			return lb;
	} else {
		alert('Sorry, geolocation unavailable.');
	}
}
function _compareRouteName(c) {
	return function (a, b) {
		var aS = c(a);
		var bS = c(b);
		if (aS < bS) return -1;
		if (aS > bS) return 1;
		return 0;
	};
}
function insert_display_element(id, orig) {
	return insert_display_element3(id, orig, null);
}
function insert_display_element3(id, orig, doBefore) {
	var divElement = document.createElement('div');
	divElement.setAttribute('id', id);
	orig.insertBefore(divElement, doBefore);
	return divElement;
}
function routeSelector_step2(rs, data, main) {
	if (!(data instanceof Array)) {
		return;
	}
	var elements = Array();
	for (var i = 0; i < data.length; i++) {
		var opt = document.createElement('option');
		opt.setAttribute("value", i);
		var v = data[i].l;
		if (v != undefined && v !== null) {
			opt.innerText = v;
			data[i].number = i;
			elements.push(opt);
		}
	}
	elements.sort(_compareRouteName(a => String(a.innerHTML).toUpperCase()));
	var topOpt = document.createElement('option');
	topOpt.innerText = "Select route...";
	topOpt.setAttribute('value', '-1');
	rs.add(topOpt);
	for (var e in elements) rs.add(elements[e]);
	var routeSelectorWrapper = document.createElement('div');
	routeSelectorWrapper.setAttribute('id', 'route-selector-wrapper');
	routeSelectorWrapper.insertBefore(rs, null);
	main.insertBefore(routeSelectorWrapper, null);
	insert_display_element('route_tables', main);
}
function displayRoute(rId) {
	var tables = document.getElementById("route_tables");
	if (tables === null) return;
	tables.innerHTML = '<div id="no-routes" style="display: none">No trips \
	currently running for this route. Please try again later or select \
	a different route.</div><div id="trip-count">Loading...</div> \
	<div>This is a <span style="font-weight: bold;" id="typeofroute"></span> route.</div> \
	<div id="trip-tables"></div>';
	routeTitleElem = insert_display_element3('route_title',
				tables, document.getElementById("no-routes"));
	var title = routeTitleElem;
	title.style.backgroundColor = "#" + rId.b;
	title.style.color = "#" + rId.f;
	title.style.fontSize = "24px";
	title.innerText = rId.s + ": " + rId.l;
	if (rId.tripInfo == undefined || rId.tripInfo === null) {
		fetchFile(rId._, /* @callback */ function (xhr, resp) {
			rId.tripInfo = resp;
			initStopData(() => processRoute(rId, tables));
		}, null);
	} else {
		initStopData(() => processRoute(rId, tables));
	}
	let typeofroute = document.getElementById('typeofroute');
	if (typeofroute) {
		const types = ["light rail", "subway", "train", "bus", "ferry", "cable car", "gondola", "funicular"];
		typeofroute.innerText = types[Number(rId.t)];
	}
}
function routeTimer(refreshText) {
	clearTimeout(timerNumber);
	var currentTime = getTimeNumberFromDate(new Date());
	var allElems = document.getElementsByClassName('trip-time');
	for (var i = 0; i < allElems.length; i++) {
		if (allElems[i] === null) continue;
		var t = allElems[i].getAttribute('_x_time');
		if (t != undefined && t !== null) {
			if (currentTime > Number(t)) {
				allElems[i].style.backgroundColor = '#ccccff';
			}
		}
	}
	var allTrips = currentRoute.tripsByTime;
	var numberOfTrips = 0;
	for (let i of allTrips) {
		if (currentRoute.trips[i] === null) continue;
		if (pruneOrUnpruneTrip(currentRoute.trips[i])) numberOfTrips++;
	}
	displayNoRoutes(numberOfTrips !== 0);
	var numberDisplay = document.getElementById('trip-count');
	if (refreshText && numberDisplay !== null) {
		if (isolateTrip === null) {
			numberDisplay.innerHTML = `<div id="route-desc"></div> \
				<span id="1tripN"></span> of ${allTrips.length} trips\
			running for this route. `;
			let viewAllRoutes = document.createElement('a');
			/* XXX: Note "_" route attribute and static file name change */
			viewAllRoutes.setAttribute('href', String(currentRoute._).replace(/\.json$/, '.html'));
			viewAllRoutes.innerText = 'View all trips';
			numberDisplay.insertBefore(viewAllRoutes, null);
			if (currentRoute.hasOwnProperty("u")) {
				viewAllRoutes = document.createElement('a');
				viewAllRoutes.setAttribute('href', currentRoute.u);
				viewAllRoutes.innerText = currentRoute.u;
				let divWrapper = document.createElement('div');
				divWrapper.insertBefore(viewAllRoutes, null);
				numberDisplay.insertBefore(divWrapper, null);
			}
			if (currentRoute.hasOwnProperty("d")) {
				document.getElementById('route-desc').innerText = currentRoute.d;
			}
		} else {
			numberDisplay.innerText = `Showing a single trip (${String(isolateTrip)}). `;
			var viewAllTrips = document.createElement('a');
			viewAllTrips.setAttribute('href', 'javascript:void(0);');
			viewAllTrips.addEventListener('click', function (e) {
				isolateTrip = null;
				routeTimer(true);
			}, null);
			viewAllTrips.innerText = 'Back';
			numberDisplay.insertBefore(viewAllTrips, null);
		}
	}
	if (isolateTrip === null) {
		let tripN = document.getElementById('1tripN');
		if (tripN) tripN.innerText = numberOfTrips;
	}
	timerNumber = setTimeout(() => routeTimer(false), 3000);
}
function displayNoRoutes(v) {
	var no_routes = document.getElementById("no-routes");
	if (no_routes) no_routes.style.display = v ? 'none' : 'block';
}
function pruneOrUnpruneTrip(tripTableElement) {
	var cInstant = new Date();
	var currentTime = getTimeNumberFromDate(cInstant);
	/* Check service match */
	var sd = safeMapAccess(serviceData, tripTableElement.serviceId);
	var checkServiceDay = (dateObj) => {
		let result = true;
		var currentDate = Math.floor(dateObj.getTime() / 86400000 - dateObj.getTimezoneOffset() / 1440.0);
		if (sd != undefined) {
			var startDate = Number(sd.s);
			var endDate = Number(sd.e);
			var dayMask = Number(sd.d);
			if (currentDate < startDate || currentDate > endDate) {
				result = false;
			} else {
				result = false;
				var currentDay = cInstant.getDay();
				if (currentDay === 0) currentDay = 7;
				if (((1 << currentDay) & dayMask) !== 0) result = true;
				if (Array.isArray(sd['-']) && sd['-'].includes(currentDate - startDate)) result = false;
				if (Array.isArray(sd['+']) && sd['+'].includes(currentDate - startDate)) result = true;
			}
		}
		return result;
	};
	var u = Number(tripTableElement.upperBound);
	var l = Number(tripTableElement.lowerBound);
	var result = isolateTrip !== null ?
			isolateTrip === tripTableElement.id
			: (checkServiceDay(cInstant) && currentTime > l && currentTime < u)
			|| (checkServiceDay(new Date(cInstant.getTime() - 86400000)) && (currentTime + 43200 > l) && (currentTime + 43200 < u));
	var display = result ? 'inline-block' : 'none';
	if (result) {
		if (!tripTableElement.table) {
			tripTableElement.genTable();
		}
		let tables = document.getElementById('trip-tables');
		let found = false;
		for (let i = 0; i < tables.children.length; i++) {
			if (tables.children.item(i).getAttribute("_x_tripid") === tripTableElement.table.getAttribute("_x_tripid")) {
				found = true;
				break;
			}
		}
		if (!found) {
			tables.insertBefore(tripTableElement.table, null);
		}
	}
	if (tripTableElement.table) {
		tripTableElement.table.style.display = display;
	}
	return result;
}
function processRoute(rId, tables) {
	initService(() => processRoute2(rId, tables));
}
function processRoute2(rId, tables) {
	clearTimeout(timerNumber);
	currentRoute = rId;
	if (rId.trips == undefined) rId.trips = {};
	document.title = rId.s + ": " + rId.l;
	let currentTime = getTimeNumberFromDate(new Date());
	var BORDER_DELTA = 300; /* 10 minutes */
	let generateTable = function(i, dryRun, singleId) {
		dryRun = dryRun ? true : false;
		var o = {};
		if (singleId) o[singleId] = rId.tripInfo[i].d[singleId];
		var tripList = singleId ? o : rId.tripInfo[i].d;
		var stopList = rId.tripInfo[i].s;
		for (let tripId in tripList) {
			/* Individual trips */
			let tripTable = dryRun ? null : document.createElement('table');
			let headingTr = dryRun ? null : document.createElement('tr');
			let headingTd = dryRun ? null : document.createElement('td');
			let earliestTime = 32766;
			let latestTime = -32768;
			if (!dryRun) {
				tripTable.setAttribute('border', 1);
				headingTd.setAttribute('colspan', 2);
				headingTd.style.fontWeight = 'bold';
				let headingLink = document.createElement('a');
				headingLink.addEventListener('click', function (e) {
					isolateTrip = this.getAttribute("_x_tripid");
					routeTimer(true);
				}, false);
				headingLink.setAttribute('href', 'javascript:void(0);');
				headingLink.setAttribute("_x_tripid", tripId);
				let headingText = rId.tripInfo[i].h;
				if (!headingText || headingText == "") headingText = tripId;
				headingLink.innerText = headingText;
				headingTd.insertBefore(headingLink, null);
				if (agency_specific_operations !== null && 
						agency_specific_operations.get_direction_string != undefined) {
					var dirString = String(agency_specific_operations.get_direction_string(rId, rId.tripInfo[i].x));
					if (dirString.length !== 0) {
						var dirText = document.createElement('span');
						dirText.innerText = ' (' + dirString + ')';
						headingTd.insertBefore(dirText, null);
					}
				}
				tripTable.setAttribute('_x_tripid', tripId);
				tripTable.setAttribute('class', 'trip-table');
				headingTr.insertBefore(headingTd, null);
				tripTable.insertBefore(headingTr, null);
			}
			let x = true;
			for (let stopN in stopList) {
				/* Individual stops in each trip */
				let stopTr = dryRun ? null : document.createElement('tr');
				let stopNameTd = dryRun ? null : document.createElement('td');
				let stopTimeTd = dryRun ? null : document.createElement('td');
				let stopId = stopList[stopN][0];
				let delta = Number(tripList[tripId].d);
				let arrivalTime = Number(stopList[stopN][1]) + delta;
				let departureTime = Number(stopList[stopN][2]) + delta;
				if (Number.isNaN(departureTime)) departureTime = arrivalTime;
				let stopInfo = safeMapAccess(stopData, stopId);
				if (!dryRun) {
					/* print stop name */
					if (stopInfo !== null) {
						stopNameTd.setAttribute('_x_stop_name', stopId);
						var stopNumber = Number(stopInfo.number);
						if (Number.isSafeInteger(stopNumber)) {
							var aElem = document.createElement('a');
							aElem.setAttribute('href', `#stop-${stopNumber}`);
							aElem.innerText = stopInfo.n;
							aElem.setAttribute('class', 'stop-name');
							stopNameTd.insertBefore(aElem, null);
						} else {
							stopNameTd.innerText = stopInfo.n;
						}
						stopNameTd.setAttribute('title', stopInfo.c);
					} else {
						stopNameTd.innerText = "??? " + stopId;
					}
					/* If already passed, shade in blue */
					if (currentTime > arrivalTime) {
						stopTimeTd.style.backgroundColor = '#ccccff';
					}
					/* If selected, bold it */
					if (stopInfo && stopInfo.number === showStopOnTrip) {
						stopNameTd.style.fontWeight = 'bold';
					}
					stopNameTd.style.textAlign = 'left';
					if (x = !x) {
						stopNameTd.style.backgroundColor = '#ddd';
					}
					stopNameTd.setAttribute('_x_originalcolor', x ? '#ddd' : '#f8f8f8');
				}
				/* earliest/latest time (display only if within) */
				var earliestTimeT = Math.min(earliestTime, arrivalTime);
				var latestTimeT = Math.max(latestTime, departureTime);
				if (!Number.isNaN(earliestTimeT) && arrivalTime != 32767) earliestTime = earliestTimeT;
				if (!Number.isNaN(latestTimeT) && departureTime != 32767) latestTime = latestTimeT;
				/* print stop time */
				if (!dryRun) {
					stopTimeTd.innerText = String(getTimeNumber(arrivalTime)) +
							(arrivalTime !== departureTime ? ' - ' + getTimeNumber(departureTime) : '');
					stopTimeTd.setAttribute('class', 'trip-time');
					stopTimeTd.setAttribute('_x_time', arrivalTime);
					stopTr.insertBefore(stopNameTd, null);
					stopTr.insertBefore(stopTimeTd, null);
					tripTable.insertBefore(stopTr, null);
				}
			}
			if (!dryRun) {
				tripTable.setAttribute("_x_trip_lowerbound", earliestTime - BORDER_DELTA);
				tripTable.setAttribute("_x_trip_upperbound", latestTime + BORDER_DELTA);
				var serviceTime = tripList[tripId].s;
				tripTable.setAttribute("_x_trip_serviceid", serviceTime);
				pruneOrUnpruneTrip(tripTable);
				rId.trips[tripId].table = tripTable;
			} else {
				if (rId.trips[tripId] == undefined) {
					rId.trips[tripId] = {
						genTable: function() {generateTable(i, false, tripId)},
						table: null,
						lowerBound: earliestTime - BORDER_DELTA,
						upperBound: latestTime + BORDER_DELTA,
						serviceId: tripList[tripId].s,
						id: tripId,
					};
				}
			}
		}
	};
	let nextF = () => {
		if (rId.tripsByTime == undefined) {
			rId.tripsByTime = [];
			for (let i in rId.trips) {
				rId.tripsByTime.push(i);
			}
			rId.tripsByTime.sort(_compareRouteName(a => rId.trips[a].lowerBound));
		}
		routeTimer(true);
		if (agency_specific_operations && agency_specific_operations.display_location_on_tables)
			agency_specific_operations.display_location_on_tables(fetchFile);
	}
	let number_displayed = 0;
	let numberDisplay = document.getElementById('trip-count');
	for (let i in rId.tripInfo) {
		setTimeout(() => {
			generateTable(i, true, null);
			if ((++number_displayed % 10 == 0) && numberDisplay)
				numberDisplay.innerText = `Loading ${number_displayed} of ${rId.tripInfo.length} trips...`;
			if (number_displayed == rId.tripInfo.length)
				nextF();
		}, 0);
	}
}
load();
}
