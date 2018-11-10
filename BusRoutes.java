import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;
import java.io.File;
import java.util.function.Predicate;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BusRoutes {
    public static String quote(String s) {
        return s.replace("\"", "\\\"");
    }
	public static abstract class GenericData {
		public Map<String, String> data = new HashMap<>();
		public String primaryKey;
		public void complete() {
			/* do nothing */
		}
		public String getPrimaryValue() {
			return data.getOrDefault(primaryKey, "");
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
		public String getHeading() {
		    return data.getOrDefault("trip_headsign", "");
        }
		/* Return time difference between similar trips, or -1 if dissimilar */
		public static int compress(Trip a, Trip b) {
		    if (a == null || b == null) {
		        return -1;
            }
			if (a.stopTimes.size() == 0 || b.stopTimes.size() == 0) {
				return -1;
			}
			if (!a.data.get("trip_headsign").equals(b.data.get("trip_headsign"))) {
				return -1;
			}
			int initialDifference = b.stopTimes.first().arrivalTime
					- a.stopTimes.first().arrivalTime;
			Iterator<StopTime> i = b.stopTimes.iterator();
			Iterator<StopTime> j = a.stopTimes.iterator();
			while (i.hasNext() && j.hasNext()) {
				StopTime bn = i.next();
				StopTime an = j.next();
				if ((bn.arrivalTime - an.arrivalTime) != initialDifference) {
					return -1;
				} else if ((bn.departureTime - an.departureTime) != initialDifference) {
					return -1;
				}
			}
			return (!i.hasNext() && !j.hasNext()) ? initialDifference : -1;
		}
		public String jsonStringifyStops() {
			return "{" + String.join(",", stopTimes.stream().map(StopTime::jsonStringify)
					.collect(Collectors.toCollection(LinkedList::new))) + "}";
		}
		public String getId() {
			return data.getOrDefault("trip_id", "");
		}
		public String jsonStringifyMetadata(int delta) {
			StringBuilder sb = new StringBuilder("{\"d\":");
			sb.append(delta);
			Optional.ofNullable(data.get("service_id")).ifPresent(s ->
						{sb.append(",\"s\":\""); sb.append(quote(s)); sb.append('"');});
			sb.append("}");
			return sb.toString();
		}
	}
	public static class UniqueTripSet extends GenericData {
		/* Only stopId used */
		public final Set<StopTime> stopList;
		public final SortedSet<Trip> trips;
		/* All stop times */
		public final Map<String, HashSet<StopTime>> stopTimesAtEachStop;
		public int count = 0;
		public static int compareTripsByArrival(Trip a, Trip b) {
			StopTime aS = a.stopTimes.first();
			StopTime bS = b.stopTimes.first();
			return Comparator.nullsFirst(Comparator.comparingInt((StopTime s)
				-> (int) s.arrivalTime)).compare(aS, bS);
		}
		public UniqueTripSet(Trip t) {
			data = Collections.unmodifiableMap(t.data);
			stopList = Collections.unmodifiableSet(t.stopTimes);
			trips = new TreeSet<Trip>(UniqueTripSet::compareTripsByArrival);
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
			trips.add(other);
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
		public Stream<String> jsonStringStream() {
		    LinkedList<String> result = new LinkedList<>();
		    Trip last = null;
		    StringBuilder tempStr = new StringBuilder();
		    Function<Trip, String> initNewTrip = (Trip t) -> String.format("{\"h\":\"%s\",\"s\":%s,\"d\":{\"%s\":%s",
                        quote(t.getHeading()), t.jsonStringifyStops(), quote(t.getId()), t.jsonStringifyMetadata(0));
		    for (Trip t : trips) {
		        int diff = Trip.compress(last, t);
		        if (diff == -1) {
		            /* Our trip is different from the last one */
                    if (last != null) {
                        tempStr.append("}}");
                    }
                    result.add(tempStr.toString());
                    tempStr = new StringBuilder(initNewTrip.apply(t));
		            last = t;
                } else {
		            tempStr.append(String.format(",\"%s\":%s", quote(t.getId()), t.jsonStringifyMetadata(diff)));
                }
            }
            if (last != null) {
                tempStr.append("}}");
            }
            result.add(tempStr.toString());
            return result.stream();
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
		public String jsonStringifyMetadata() {
			StringBuilder sb = new StringBuilder("\"");
			sb.append(quote(data.getOrDefault("stop_id", "")));
			sb.append("\":{");
			boolean isFirst = true;
			String[][] attr = new String[][] {{"stop_code", "c"}, {"stop_name", "n"}, {"stop_desc", "d"},
					{"stop_url", "u"}, {"zone_id", "z"}, {"stop_lat", "y"}, {"stop_lon", "x"}};
			for (String[] a : attr) {
				String v = data.get(a[0]);
				if (v != null && !v.isEmpty()) {
					if (!isFirst) sb.append(',');
					if (a[1].matches("[xy]")) {
						sb.append(String.format("\"%s\":%.7f", a[1], Double.parseDouble(v)));
					} else {
						sb.append(String.format("\"%s\":\"%s\"", a[1], quote(v)));
					}
					isFirst = false;
				}
			}
			sb.append('}');
			return sb.toString();
		}
	}
	public static class StopTime implements Comparable<StopTime> {
		public static final short INVALID_TIME = 32767;
		public short arrivalTime;
		public short departureTime;
		public Trip tripId;
		public Stop stopId;
		public long stopSequence;
		public long stopOrder; /* always 0 and counting up */
		public boolean sameId(StopTime other) {
			return this.stopId == other.stopId;
		}
		public StopTime() {}
		public static short parseTime(String time) {
			String[] timeParts = time.split(":");
			int hours = Integer.parseInt(timeParts[0], 10);
			int minutes = Integer.parseInt(timeParts[1], 10);
			int seconds = Integer.parseInt(timeParts[2], 10);
			int n = (hours * 1800 + minutes * 30 + seconds / 2) - 32768;
			if (n < -32768 || n > 32766) {
				/* invalid time */
				return INVALID_TIME;
			}
			return (short) n;
		}
		public static String expandTime(short time) {
			if (time == INVALID_TIME) return "";
			int t = ((int) time) + 32768;
			int h = t / 1800;
			int m = (t % 1800) / 30;
			int s = (t % 30) * 2;
			if (s != 0)
				return String.format("%02d:%02d:%02d", h, m, s);
			else
				return String.format("%02d:%02d", h, m);
		}
		public StopTime(String[] header, String[] data,
		Map<String, ? extends Stop> stops, Map<String, ? extends Trip> trips) {
			for (int i = 0; i < Math.min(header.length, data.length); i++) {
				switch (header[i]) {
					case "arrival_time":
						arrivalTime = parseTime(data[i]);
						break;
					case "departure_time":
						departureTime = parseTime(data[i]);
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
			return String.format("%s -> %s @ %s #%d", expandTime(arrivalTime),
			expandTime(departureTime), stopId.toString(), stopSequence);
		}
		public String stopIdAndOrder() {
			return String.format("%d %s", stopOrder, stopId.toString());
		}
		public String jsonStringify() {
		    String sName = quote(stopId.data.getOrDefault("stop_id", ""));
		    if (arrivalTime != departureTime) {
                return String.format("\"%s\":[%d,%d]", sName, arrivalTime, departureTime);
            } else {
		        return String.format("\"%s\":[%d]", sName, arrivalTime);
            }
        }
	}
	public static class ServiceGroup extends GenericData {
    	public Set<DayOfWeek> available = null;
    	public LocalDate startDate, endDate;
    	public String serviceId;
    	public final Map<LocalDate, String> exceptions = new TreeMap<>();
    	public static LocalDate parseDate(String d) {
    		SimpleDateFormat s = new SimpleDateFormat("yyyyMMdd");
    		try {
				return LocalDate.ofEpochDay(s.parse(d).toInstant().toEpochMilli() / 86400000);
			} catch (Exception e) {
    			return LocalDate.of(1970, 1, 1);
			}
		}
    	@Override
		public void complete() {
    		available = EnumSet.noneOf(DayOfWeek.class);
    		for (DayOfWeek e : DayOfWeek.values()) {
    			String s = data.getOrDefault(e.toString().toLowerCase(), "0");
    			if (s.equals("1")) available.add(e);
			}
    		startDate = parseDate(data.getOrDefault("start_date", ""));
    		endDate = parseDate(data.getOrDefault("end_date", ""));
    		serviceId = data.getOrDefault("service_id", "");
    		data.clear();
		}
		@Override
		public String getPrimaryValue() {
    		return serviceId;
		}
		public String jsonStringify() {
    		StringBuilder sb = new StringBuilder(String.format("\"%s\":{", quote(serviceId)));
    		Set<LocalDate> added = new TreeSet<>();
    		Set<LocalDate> removed = new TreeSet<>();
    		for (Map.Entry<LocalDate, String> e : exceptions.entrySet()) {
    			if ("add".equals(e.getValue())) {
    				added.add(e.getKey());
				} else if ("remove".equals(e.getValue())) {
    				removed.add(e.getKey());
				}
			}
			long startDay = startDate.toEpochDay();
    		long endDay = endDate.toEpochDay();
    		long bitmask = 0;
    		for (DayOfWeek i : available) {
    			bitmask |= 1 << i.ordinal();
			}
			Function<Set<LocalDate>, String> p = s -> {
    			return Arrays.toString(s.stream().mapToLong(d -> d.toEpochDay() - startDay).toArray());
			};
    		sb.append(String.format("\"s\":%d,\"e\":%d,\"d\":%d,\"+\":%s,\"-\":%s}", startDay, endDay, bitmask,
					p.apply(added), p.apply(removed)));
    		return sb.toString();
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
		return String.format("%s_%08x", fileName.toLowerCase()
				.replaceAll("\\W", "\\_"), fileName.hashCode());
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
			String keyV = result.data.get(key);
			result.complete();
			result.primaryKey = key;
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
	public static PrintStream openTextOrDie(String name) {
    	PrintStream r = null;
		try {
			r = new PrintStream(name);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(99);
		}
		return r;
	}
	public static void main(String[] args) {
		FileInputStream routeStream = null;
		FileInputStream tripStream = null;
		FileInputStream stopStream = null;
		FileInputStream stopTimeStream = null;
		FileInputStream calendarStream = null, calendarDateStream = null;
		try {
			new File("public/routes/").mkdirs();
			new File("public/meta/").mkdirs();
			routeStream = new FileInputStream("routes.txt");
			tripStream = new FileInputStream("trips.txt");
			stopStream = new FileInputStream("stops.txt");
			stopTimeStream = new FileInputStream("stop_times.txt");
			calendarStream = new FileInputStream("calendar.txt");
			calendarDateStream = new FileInputStream("calendar_dates.txt");
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
		Scanner calendarScanner = new Scanner(calendarStream);
		Scanner calendarDateScanner = new Scanner(calendarDateStream);
		HashMap<String, ServiceGroup> g = parseData(calendarScanner, "service_id", ServiceGroup::new);
		/* calendar_dates.txt also has no primary key */
		parseCalendar(g, calendarDateScanner);
		routeScanner.close();
		tripScanner.close();
		stopScanner.close();
		stopTimeScanner.close();
		calendarScanner.close();
		calendarDateScanner.close();
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
			/* STEP 1: static route html files */
			String commonHtmlName = getHtmlFileName(r.data.get("route_id"));
			PrintStream routeData = openTextOrDie(String.format("public/routes/%s.html", commonHtmlName));
			System.out.print("Processing " + r.getLongName() + "... ");
			routeData.println("<!DOCTYPE html>\n<html><head><title>");
			routeData.print(sanitizeHtml(r.getFullName(), false));
			routeData.print("</title></head><body><h1>");
			routeData.print(sanitizeHtml(r.getFullName(), false));
			routeData.print("</h1>");
			for (UniqueTripSet s : r.uniqueTrips) {
				routeData.print(String.format("<h2>%s</h2>", sanitizeHtml(s.data.get("trip_headsign"), true)));
				routeData.print("<table border=1>");
				for (StopTime st : s.stopList) {
					routeData.print("<tr><td style=\"white-space: nowrap;\">");
					routeData.print(sanitizeHtml(st.stopId.data.get("stop_name"), true));
					routeData.print("</td>");
					Set<StopTime> tempAT = s.stopTimesAtEachStop.get(st.stopIdAndOrder());
					StopTime[] allTimes = tempAT.toArray(new StopTime[0]);
					Arrays.sort(allTimes, Comparator.comparingInt(n -> (int) n.arrivalTime));
					Trip lastTrip = null;
					short lastArrivalTime = StopTime.INVALID_TIME;
					for (StopTime indivTime : allTimes) {
						StringBuilder sb = new StringBuilder();
						/* Eliminate duplicate trips */
						if (indivTime.tripId != lastTrip) {
							lastTrip = indivTime.tripId;
						} else {
							continue;
						}
						/* Eliminate duplicate arrival times */
						if (indivTime.arrivalTime != lastArrivalTime) {
							lastArrivalTime = indivTime.arrivalTime;
						} else {
							continue;
						}
						sb.append(StopTime.expandTime(indivTime.arrivalTime));
						if (indivTime.departureTime != indivTime.arrivalTime) {
							sb.append(" - ");
							sb.append(StopTime.expandTime(indivTime.departureTime));
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
			System.out.print("html done, ");
			/* STEP 2: json */
            PrintStream routeJsonData = openTextOrDie(String.format("public/routes/%s.json", commonHtmlName));
            routeJsonData.print(Arrays.toString(r.uniqueTrips.stream().flatMap(UniqueTripSet::jsonStringStream)
					.filter(((Predicate<String>) String::isEmpty).negate()).toArray()));
            routeJsonData.close();
			System.out.println("json done.");
		}
		/* STEP 3: stops database */
		PrintStream stopDB = openTextOrDie("public/meta/stops.json");
		stopDB.print("{");
		stopDB.print(stops.values().parallelStream().map(Stop::jsonStringifyMetadata).collect(Collectors.joining(",")));
		stopDB.print("}");
		stopDB.close();
		PrintStream serviceDB = openTextOrDie("public/meta/service.json");
		serviceDB.print("{");
		serviceDB.print(g.values().parallelStream().map(ServiceGroup::jsonStringify).collect(Collectors.joining(",")));
		serviceDB.print("}");
		serviceDB.close();
	}

	private static void parseCalendar(Map<String,ServiceGroup> g, Scanner calendarDateScanner) {
    	String keys = calendarDateScanner.nextLine();
    	String[] kl = keys.split(",");
    	while (calendarDateScanner.hasNextLine()) {
    		String values = calendarDateScanner.nextLine();
    		LocalDate exceptionDate = null;
    		int exceptionVal = 0;
    		String serviceId = null;
    		String[] vl = values.split(",");
    		for (int i = 0; i < Math.min(kl.length, vl.length); i++) {
    			switch (kl[i].intern()) {
					case "date":
						exceptionDate = ServiceGroup.parseDate(vl[i]);
						break;
					case "exception_type":
						exceptionVal = Integer.parseInt(vl[i]);
						break;
					case "service_id":
						serviceId = vl[i];
						break;
				}
			}
			ServiceGroup rsg = g.get(serviceId);
    		if (exceptionVal != 0 && rsg != null) {
    			rsg.exceptions.put(exceptionDate, exceptionVal == 1 ? "add" : "remove");
			}
		}
	}
}