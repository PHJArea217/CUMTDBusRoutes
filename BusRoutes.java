import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.Comparator;
import java.util.Collections;
import java.io.File;
import java.util.function.Predicate;
import java.time.LocalTime;
import java.time.Instant;
public class BusRoutes {
	public static abstract class GenericData {
		public Map<String, String> data = new HashMap<>();
		public void complete() {
			/* do nothing */
		}
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for (Map.Entry<String, String> d : data.entrySet()) {
				sb.append(String.format("[%s: %s]", d.getKey(), d.getValue()));
			}
			sb.append("]");
			return sb.toString();
		}
	}
	public static class Trip extends GenericData {
		public TreeSet<StopTime> stopTimes = new TreeSet<>();
		public void orderStopTimes() {
			long order = 0;
			for (StopTime t : stopTimes) {
				t.stopOrder = order++;
			}
		}
	}
	public static class UniqueTripSet extends GenericData {
		/* Only stopId used */
		public final Set<StopTime> stopList;
		/* All stop times */
		public Map<String, HashSet<StopTime>> stopTimesAtEachStop;
		public int count = 0;
		public UniqueTripSet(Trip t) {
			data = Collections.unmodifiableMap(t.data);
			stopList = Collections.unmodifiableSet(t.stopTimes);
			stopTimesAtEachStop = new HashMap<>();
			for (StopTime tm : t.stopTimes) {
				stopTimesAtEachStop.put(tm.stopIdAndOrder(), new HashSet<>());
			}
			addTrip(t);
		}
		public boolean isCompatibleWith(Trip other) {
			Iterator<StopTime> thisIterator = this.stopList.iterator();
			Iterator<StopTime> otherIterator = other.stopTimes.iterator();
			while (thisIterator.hasNext() && otherIterator.hasNext()) {
				if (!thisIterator.next().sameId(otherIterator.next())) {
					return false;
				}
			}
			return !thisIterator.hasNext() && !otherIterator.hasNext();
		}
		public boolean addTrip(Trip other) {
			if (!isCompatibleWith(other)) {
				return false;
			}
			for (StopTime s : other.stopTimes) {
				stopTimesAtEachStop.get(s.stopIdAndOrder()).add(s);
			}
			count++;
			return true;
		}
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(count);
			sb.append(" [");
			for (StopTime s : stopList) {
				sb.append(String.format("[%s]", s.toString()));
			}
			sb.append("]");
			return sb.toString();
		}
	}
	public static class Stop extends GenericData {
		@Override
		public String toString() {
			String g = data.get("stop_name");
			if (g == null) {
				return "";
			}
			return g;
		}
	}
	public static class StopTime implements Comparable<StopTime> {
		public LocalTime arrivalTime;
		public boolean arrivalIsNextDay;
		public LocalTime departureTime;
		public boolean departureIsNextDay;
		public Trip tripId;
		public Stop stopId;
		public long stopSequence;
		public long stopOrder; /* always 0 and counting up */
		public boolean sameId(StopTime other) {
			return this.stopId == other.stopId;
		}
		public StopTime() {}
		private static boolean parseTime(String time, LocalTime[] store) {
			String[] timeParts = time.split(":");
			boolean result = false;
			int hours = Integer.parseInt(timeParts[0], 10);
			if (hours >= 24) {
				hours -= 24;
				result = true;
			}
			int minutes = Integer.parseInt(timeParts[1], 10);
			int seconds = Integer.parseInt(timeParts[2], 10);
			store[0] = LocalTime.of(hours, minutes, seconds);
			return result;
		}
		public StopTime(String[] header, String[] data,
		Map<String, ? extends Stop> stops, Map<String, ? extends Trip> trips) {
			for (int i = 0; i < Math.min(header.length, data.length); i++) {
				LocalTime[] store = new LocalTime[1];
				switch (header[i]) {
					case "arrival_time":
						arrivalIsNextDay = parseTime(data[i], store);
						arrivalTime = store[0];
						break;
					case "departure_time":
						departureIsNextDay = parseTime(data[i], store);
						departureTime = store[0];
						break;
					case "stop_id":
						stopId = stops.get(data[i]);
						break;
					case "trip_id":
						tripId = trips.get(data[i]);
						break;
					case "stop_sequence":
						stopSequence = Long.parseLong(data[i]);
						break;
				}
			}
			tripId.stopTimes.add(this);
		}
		@Override
		public int compareTo(StopTime other) {
			return Long.compare(this.stopSequence, other.stopSequence);
		}
		@Override
		public String toString() {
			return String.format("%s -> %s @ %s #%d", arrivalTime.toString(),
			departureTime.toString(), stopId.toString(), stopSequence);
		}
		public String stopIdAndOrder() {
			return String.format("%d %s", stopOrder, stopId.toString());
		}
	}
	public static enum RouteType {
		LIGHT_RAIL ("Tram/Light rail", 0),
		SUBWAY ("Subway/Metro", 1),
		RAIL ("Rail", 2),
		BUS ("Bus", 3),
		FERRY ("Ferry", 4),
		CABLE_CAR ("Cable car", 5),
		GONDOLA ("Gondola", 6),
		FUNICULAR ("Funicular", 7);
		private final String friendlyName;
		private final int id;
		private RouteType(String friendlyName, int id) {
			this.friendlyName = friendlyName;
			this.id = id;
		}
		public static RouteType byId(int id) {
			for (RouteType t : values()) {
				if (id == t.id) {
					return t;
				}
			}
			return null;
		}
		@Override
		public String toString() {
			return friendlyName;
		}
	}
	public static class Route extends GenericData implements Comparable<Route> {
		public long sortOrder = -1;
		public HashMap<String, Trip> trips = new HashMap<>();
		public Set<UniqueTripSet> uniqueTrips = new HashSet<>();
		@Override
		public int compareTo(Route other) {
			if (other.sortOrder > sortOrder) {
				return 1;
			} else if (other.sortOrder < sortOrder) {
				return -1;
			}
			return 0;
		}
		@Override
		public void complete() {
			try {
				sortOrder = Long.parseLong(data.get("route_sort_order"));
			} catch (Exception e) {
				
			}
		}
		public String getShortName() {
			String r = data.get("route_short_name");
			return r == null ? "" : r;
		}
		public String getLongName() {
			String r = data.get("route_long_name");
			return r == null ? "" : r;
		}
		public String getFullName() {
			String s = data.get("route_short_name");
			String l = data.get("route_long_name");
			if (s == null) {
				return l == null ? "" : l;
			}
			if (l == null) return s;
			return s + ": " + l;
		}
	}
	public static String getHtmlFileName(String fileName) {
		return fileName.toLowerCase().replaceAll("\\W", "\\_");
	}
	public static String sanitizeHtml(String original, boolean isHtml) {
		if (isHtml) {
			return original.replace("<", "&lt;");
		} else {
			return original.replace("<", "%3C").replace(">", "%3E")
				.replace("\"", "%22").replace(";", "%3B");
		}
	}
	public static <T extends GenericData> HashMap<String, T> parseData
		(Scanner input, String key, Supplier<? extends T> creator) {
		HashMap<String, T> ret = new HashMap<>();
		/* Read keys from first line */
		String firstLine = input.nextLine();
		String[] keys = firstLine.split(",");
		/* Read each element into map */
		while (input.hasNextLine()) {
			String[] values = input.nextLine().split(",");
			T result = creator.get();
			for (int i = 0; i < Math.min(keys.length, values.length); i++) {
				result.data.put(keys[i].intern(), values[i].intern());
			}
			result.complete();
			String keyV = result.data.get(key);
			ret.put(keyV, result);
		}
		return ret;
	}
	public static <T> T onlyIf(T obj, Predicate<? super T> tester) {
		if (obj == null) {
			return null;
		}
		if (tester.test(obj)) {
			return obj;
		} else {
			return null;
		}
	}
	public static void insertStopTimes(HashMap<String, ? extends Trip> trips,
							HashMap<String, ? extends Stop> stops, Scanner s) {
		String firstLine = s.nextLine();
		String[] keys = firstLine.split(",");
		while (s.hasNextLine()) {
			String[] values = s.nextLine().split(",");
			StopTime st = new StopTime(keys, values, stops, trips);
		}
		for (Trip t : trips.values()) {
			t.orderStopTimes();
		}
	}
	public static void main(String[] args) {
		FileInputStream routeStream = null;
		FileInputStream tripStream = null;
		FileInputStream stopStream = null;
		FileInputStream stopTimeStream = null;
		try {
			new File("public/routes/").mkdirs();
			routeStream = new FileInputStream("routes.txt");
			tripStream = new FileInputStream("trips.txt");
			stopStream = new FileInputStream("stops.txt");
			stopTimeStream = new FileInputStream("stop_times.txt");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}
		/* Generate Route Data, Trip Data, Stop Data, Stop Time Data */
		Scanner routeScanner = new Scanner(routeStream);
		HashMap<String, Route> routes = parseData(routeScanner, "route_id", Route::new);
		Scanner tripScanner = new Scanner(tripStream);
		HashMap<String, Trip> trips = parseData(tripScanner, "trip_id", Trip::new);
		Scanner stopScanner = new Scanner(stopStream);
		HashMap<String, Stop> stops = parseData(stopScanner, "stop_id", Stop::new);
		Scanner stopTimeScanner = new Scanner(stopTimeStream);
		/* stop_times.txt has no primary key, instead they are children of Trip */
		insertStopTimes(trips, stops, stopTimeScanner);
		routeScanner.close();
		tripScanner.close();
		stopScanner.close();
		stopTimeScanner.close();
		/* Output Route Data in HTML */
		PrintStream index = null;
		try {
			index = new PrintStream("index-dynamic.html");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}
		index.println("<ul>");
		Route[] routeArray = routes.values().toArray(new Route[0]);
		Arrays.sort(routeArray, Comparator.comparing((Function<Route, String>) r -> r.data.get("route_short_name")));
		for (Route r : routeArray) {
			String backColor = onlyIf(r.data.get("route_color"), t -> t.matches("[0-9a-f]{6}"));
			String foreColor = onlyIf(r.data.get("route_text_color"), t -> t.matches("[0-9a-f]{6}"));
			if (backColor == null) backColor = "ffffff";
			if (foreColor == null) foreColor = "000000";
			index.print(String.format("<li><span style=\"background-color: #%s; color: #%s;\">", backColor, foreColor));
			index.format("%s</span> (<a href=\"routes/%s.html\">View stops</a>)</li>", 
					sanitizeHtml(r.getFullName(), false), getHtmlFileName(r.data.get("route_id")));
		}
		index.format("</ul><p>Last Updated: %s</p>", Instant.now());
		index.close();
		/* Generate a list of unique trips for each route. Each trip is unique if
		it has the same set of stops. */
		for (Trip t : trips.values()) {
			Route tripRoute = routes.get(t.data.get("route_id"));
			boolean found = false;
			for (UniqueTripSet ts : tripRoute.uniqueTrips) {
				if (ts.addTrip(t)) {
					found = true;
					break;
				}
			}
			if (!found) {
				tripRoute.uniqueTrips.add(new UniqueTripSet(t));
			}
		}
		for (Route r : routes.values()) {
			PrintStream routeData = null;
			try {
				routeData = new PrintStream("public/routes/" +
				getHtmlFileName(r.data.get("route_id")) + ".html");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(99);
			}
			System.out.print("Processing " + r.getLongName() + "... ");
			routeData.println("<!DOCTYPE html>\n<html><head><title>");
			routeData.print(sanitizeHtml(r.getFullName(), false));
			routeData.print("</title></head><body><h1>");
			routeData.print(sanitizeHtml(r.getFullName(), false));
			routeData.print("</h1>");
			for (UniqueTripSet s : r.uniqueTrips) {
				routeData.print(String.format("<h2>%s</h2>", s.data.get("trip_headsign")));
				routeData.print("<table border=1>");
				for (StopTime st : s.stopList) {
					routeData.print("<tr><td style=\"white-space: nowrap;\">");
					routeData.print(sanitizeHtml(st.stopId.data.get("stop_name"), false));
					routeData.print("</td>");
					Set<StopTime> tempAT = s.stopTimesAtEachStop.get(st.stopIdAndOrder());
					StopTime[] allTimes = tempAT.toArray(new StopTime[0]);
					Arrays.sort(allTimes, Comparator.comparing(t -> ((StopTime) t).arrivalIsNextDay).thenComparing(t -> ((StopTime) t).arrivalTime));
					Trip lastTrip = null;
					LocalTime lastArrivalTime = null;
					for (StopTime indivTime : allTimes) {
						StringBuilder sb = new StringBuilder();
						/* Eliminate duplicate trips */
						if (indivTime.tripId != lastTrip) {
							lastTrip = indivTime.tripId;
						} else {
							continue;
						}
						/* Eliminate duplicate arrival times */
						if (!indivTime.arrivalTime.equals(lastArrivalTime)) {
							lastArrivalTime = indivTime.arrivalTime;
						} else {
							continue;
						}
						sb.append(indivTime.arrivalTime);
						if (!indivTime.departureTime.equals(indivTime.arrivalTime)) {
							sb.append(" - ");
							sb.append(indivTime.departureTime);
						}
						routeData.format("<td>%s</td>", sb.toString());
					}
					routeData.println("</tr>");
				}
				routeData.println("</table>");
			}
			routeData.println("<a href=\"../\">Back to Main Page</a>");
			routeData.println("</body></html>");
			routeData.close();
			System.out.println("done.");
		}
	}
}