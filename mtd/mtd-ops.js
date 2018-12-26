/*eslint-disable no-unused-vars */
function MTDOperations() {
	this.get_direction_string = function(route_obj, direction_id) {
		switch (route_obj.s) {
			case "1":
			case "100":
			case "3":
			case "30":
			case "11":
			case "110":
			case "13":
			case "130":
			case "22":
			case "220":
				return direction_id ? 'S' : 'N';
			case "2":
			case "20":
				return direction_id ? 'U' : 'C';
			case "4":
			case "5":
			case "50":
			case "6":
			case "7":
			case "70":
			case "8":
			case "10":
			case "12":
			case "120":
			case "14":
				return direction_id ? 'W' : 'E';
			case "9":
			case "16":
			case "180":
				return direction_id ? 'B' : 'A';
			default: /* e.g. Raven */
				return '';
		}
	};
	this.last_api_payload = null;
	this.last_api_timer_number = -1;
	this.currently_displayed_table = null;
	this.repeatCallback = null;
	this.kickoff_api = function (fetchFileFunc, callback) {
		var fetchMe = (doIt, o) => {
			clearTimeout(o.last_api_timer_number);
			fetchFileFunc("https://apps-vm2-cdn.peterjin.org/apps/vehicle-svr/vehicles.json", function (xhr, res) {
				o.last_api_payload = res;
				if (doIt && callback) callback();
				else if (o.repeatCallback !== null) o.repeatCallback();
			}, null);
			o.last_api_timer_number = setTimeout(() => fetchMe(false, o), 40000);
		}
		if (this.last_api_payload !== null) {
			if (callback) callback();
			return;
		}
		if (this.last_api_timer_number === -1) fetchMe(true, this);
	};
	this.display_location_on_tables = (fetchFileFunc) => {
		this.repeatCallback = () => br_rt_departures_obj_export
			.displayLocationOnTripTable(this.last_api_payload.vehicles);
		this.kickoff_api(fetchFileFunc, this.repeatCallback);
	}
	this.display_rt_on_table = function (routeList, stopId, stopData,
		fetchFileFunc, populateRouteData, populateBlockData, tableElem, limit) {
		var o = this;
		this.currently_displayed_table = tableElem;
		this.repeatCallback = function() {
			if (o.currently_displayed_table !== null)
				br_rt_departures_obj_export.createDepartureTable(routeList, stopId, stopData,
					o.last_api_payload.vehicles, limit,
					populateRouteData, populateBlockData, o.currently_displayed_table);
		};
		tableElem.innerHTML = "<tr><td>Loading...</td></tr>";
		this.kickoff_api(fetchFileFunc, () => {
			br_rt_departures_obj_export.determineDirection = this.get_direction_string;
			this.repeatCallback();
		});
	};
	this.sms_destination_number = '35890';
	return this;
}
var mtd_ops = MTDOperations.call(new Object());
