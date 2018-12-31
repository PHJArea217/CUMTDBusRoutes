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
import java.io.BufferedOutputStream;
import java.util.function.Predicate;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;

public class BusRoutes {
	public static String quote(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			sb.append((c == '"' || c == '\\' || c > 128) ? String.format("\\u%04x", (int) c) : c);
		}
		return sb.toString();
	}
	public static String[] splitCSV(String s0) {
		ArrayList<String> s = new ArrayList<>(20);
		StringBuilder sb = new StringBuilder();
		boolean inQuote = false;
		boolean lastQuote = false;
		for (char c : s0.toCharArray()) {
			if (c == '\uFEFF') continue;
			if (c == ',' && inQuote == false) {
				lastQuote = false;
				String st = sb.toString();
				s.add("\"".equals(st) ? "" : st);
				sb = new StringBuilder();
			} else if (c == '"') {
				if (lastQuote == true) {
					sb.append('"');
					lastQuote = false;
				} else {
					lastQuote = true;
				}
				inQuote = !inQuote;
			} else {
				sb.append(c);
				lastQuote = false;
			}
		}
		s.add(sb.toString());
		return s.toArray(new String[0]);
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
		public Route routeId = null;
		private Map<String, Route> routeLookupTable = null;
		public int indexInJsonArray = -1;
		public Trip() {
				this(null);
		}
		public Trip(Map<String, Route> lookupTable) {
				super();
				routeLookupTable = lookupTable;
		}
		@Override
		public void complete() {
			if (routeLookupTable != null) {
				routeId = routeLookupTable.get(data.getOrDefault("route_id", ""));
			}
			routeLookupTable = null;
			data.putIfAbsent("block_id", String.valueOf(Math.random()));
		}
		public void orderStopTimes() {
			long order = 0;
			for (StopTime t : stopTimes) {
				t.stopOrder = order++;
			}
		}
		public String getHeading() {
			return data.getOrDefault("trip_headsign", "");
		}
		static final int compareALessThanB = Integer.MIN_VALUE + 1;
		static final int compareAGreaterThanB = Integer.MIN_VALUE + 2;
		static final int compareUndefined = Integer.MIN_VALUE;
		static Comparator<Trip> initialConditionComparator = Comparator.nullsFirst(Comparator.comparingInt((Trip t) -> t.stopTimes.size()).thenComparing(Trip::getHeading));
		/* Return time difference between similar trips, or -1 if dissimilar */
		public static int compress(Trip a, Trip b) {
			if (a == b) return 0;
			int r = initialConditionComparator.compare(a, b);
			if (r != 0) return r < 0 ? compareALessThanB : compareAGreaterThanB;
			if (a == null || a.stopTimes.size() == 0) {
				return compareUndefined;
			}
			int initialDifference = b.stopTimes.first().arrivalTime
					- a.stopTimes.first().arrivalTime;
			Iterator<StopTime> i = b.stopTimes.iterator();
			Iterator<StopTime> j = a.stopTimes.iterator();
			while (i.hasNext() && j.hasNext()) {
				StopTime bn = i.next();
				StopTime an = j.next();
				int n;
				if ((n = (bn.arrivalTime - an.arrivalTime) - initialDifference) != 0) {
					return n > 0 ? compareALessThanB : compareAGreaterThanB;
				} else if ((n = (bn.departureTime - an.departureTime) - initialDifference) != 0) {
					return n > 0 ? compareALessThanB : compareAGreaterThanB;
				}
			}
			return (!i.hasNext() && !j.hasNext()) ? initialDifference : i.hasNext() ? compareAGreaterThanB : compareALessThanB;
		}
		public static int tripCompareSort(Trip a, Trip b) {
			switch (compress(a, b)) {
				case compareALessThanB: return -1;
				case compareAGreaterThanB: return 1;
				default: return 0;
			}
		}
		public String jsonStringifyStops() {
			return "[" + String.join(",", stopTimes.stream().map(StopTime::jsonStringify)
					.collect(Collectors.toCollection(LinkedList::new))) + "]";
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
		static Comparator<StopTime> c00 = Comparator.nullsFirst(Comparator.comparingInt((StopTime s)
			-> (int) s.arrivalTime));
		static Comparator<Trip> c002 = Comparator.comparing(Trip::getPrimaryValue);
		public static int compareTripsByArrival(Trip a, Trip b) {
			StopTime aS = a.stopTimes.isEmpty() ? null : a.stopTimes.last();
			StopTime bS = b.stopTimes.isEmpty() ? null : b.stopTimes.last();
			int r = c00.compare(aS, bS);
			return r == 0 ? c002.compare(a, b) : r;
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
			Function<Trip, String> initNewTrip = (Trip t) -> {
						if (t.routeId != null) t.indexInJsonArray = t.routeId.incrementJsonArrayCounter();
						return String.format("{\"h\":\"%s\",\"s\":%s,\"x\":%s,\"d\":{\"%s\":%s",
						quote(t.getHeading()), t.jsonStringifyStops(),
						"1".equals(t.data.get("direction_id")) ? "true" : "false",
						quote(t.getId()), t.jsonStringifyMetadata(0));
			};
			Trip[] sorted = trips.stream().sorted(Trip::tripCompareSort).toArray(Trip[]::new);
			for (Trip t : sorted) {
				int diff = Trip.compress(last, t);
				if (diff < Integer.MIN_VALUE + 3) {
					/* Our trip is different from the last one */
					if (last != null) {
						tempStr.append("}}");
					}
					result.add(tempStr.toString());
					tempStr = new StringBuilder(initNewTrip.apply(t));
					last = t;
				} else {
					t.indexInJsonArray = last.indexInJsonArray;
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
		public final SortedSet<Route> servicedRoutes = new TreeSet<>();
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
			sb.append("\":{\"r\":");
			sb.append(Arrays.toString(servicedRoutes.stream().mapToInt(Route::getNumber).toArray()).replaceAll(" ",""));
			String[][] attr = new String[][] {{"stop_code", "c"}, {"stop_name", "n"}, {"stop_desc", "d"},
					{"stop_url", "u"}, {"zone_id", "z"}, {"stop_lat", "y"}, {"stop_lon", "x"}};
			for (String[] a : attr) {
				String v = data.get(a[0]);
				if (v != null && !v.isEmpty()) {
					sb.append(',');
					if (a[1].matches("[xy]")) {
						sb.append(String.format("\"%s\":%.7f", a[1], Double.parseDouble(v)));
					} else {
						sb.append(String.format("\"%s\":\"%s\"", a[1], quote(v)));
					}
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
			try {
				int hours = Integer.parseInt(timeParts[0], 10);
				int minutes = Integer.parseInt(timeParts[1], 10);
				int seconds = Integer.parseInt(timeParts[2], 10);
				int n = (hours * 1800 + minutes * 30 + seconds / 2) - 32768;
				if (n < -32768 || n > 32766)
					/* invalid time */
					return INVALID_TIME;
				return (short) n;
			} catch (Exception e) {
				return INVALID_TIME;
			}
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
						stopSequence = Long.parseLong(data[i].trim());
						break;
				}
			}
			if (tripId != null && stopId != null)
				tripId.stopTimes.add(this);
		}
		private static final Comparator<Object> hashCodeComparator = Comparator.comparing(Object::hashCode);
		@Override
		public int compareTo(StopTime other) {
			int r = Long.compare(this.stopSequence, other.stopSequence);
			return r == 0 ? hashCodeComparator.compare(this, other) : r;
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
				return String.format("[\"%s\",%d,%d]",
						sName, arrivalTime, departureTime);
			} else {
				return String.format("[\"%s\",%d]",
						sName, arrivalTime);
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
		private int number = 0;
		private final AtomicInteger jsonArrayOrderCounter = new AtomicInteger();
		private static final AtomicInteger numberCounter = new AtomicInteger();
		public HashMap<String, Trip> trips = new HashMap<>();
		public List<UniqueTripSet> uniqueTrips = new ArrayList<>();
		public Route() {
			super();
			number = numberCounter.getAndIncrement();
		}
		public int getNumber() {
			return number;
		}
		public int incrementJsonArrayCounter() {
				return jsonArrayOrderCounter.getAndIncrement();
		}
		@Override
		public int compareTo(Route other) {
			if (other.sortOrder > sortOrder) {
				return 1;
			} else if (other.sortOrder < sortOrder) {
				return -1;
			}
			return Comparator.comparingInt(Object::hashCode).compare(this, other);
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
		public String jsonStringify() {
			StringBuilder sb = new StringBuilder("{\"i\":\"");
			sb.append(quote(getPrimaryValue()));
			sb.append(String.format("\",\"_\":\"routes/%s.json\"",
				getHtmlFileName(getPrimaryValue())));
			String[][] attr = new String[][] {{"route_short_name", "s"},
				{"route_long_name", "l"}, {"route_desc", "d"},
				{"route_type", "t"}, {"route_color", "b"},
				{"route_url", "u"},
				{"route_text_color", "f"}};
			for (String[] a : attr) {
				Optional.ofNullable(data.get(a[0])).ifPresent(v ->
					sb.append(String.format(",\"%s\":\"%s\"", a[1], quote(v))));
			}
			sb.append('}');
			return sb.toString();
		}
	}
	public static String getHtmlFileName(String fileName) {
		return String.format("%s_%08x", fileName.toLowerCase()
				.replaceAll("\\W", "\\_"), fileName.hashCode());
	}
	public static String sanitizeHtml(String original, boolean isHtml) {
		if (isHtml) {
			StringBuilder sb = new StringBuilder();
			for (char c : original.toCharArray()) {
				sb.append((c == '<' || c == '&' || c > 128) ? "&#" + (int) c + ";" : c);
			}
			return sb.toString();
		} else {
			return original.replace("<", "%3C").replace(">", "%3E")
				.replace("\"", "%22").replace(";", "%3B");
		}
	}
	public static <T extends GenericData> HashMap<String, T> parseData
		(Scanner input, String key, Supplier<? extends T> creator) {
		HashMap<String, T> ret = new HashMap<>();
		if (!input.hasNextLine()) return ret;
		/* Read keys from first line */
		String firstLine = input.nextLine();
		String[] keys = splitCSV(firstLine);
		/* Read each element into map */
		for (int i = 0; i < keys.length; i++) {
			keys[i] = keys[i].trim().intern();
		}
		while (input.hasNextLine()) {
			String[] values = splitCSV(input.nextLine());
			T result = creator.get();
			for (int i = 0; i < Math.min(keys.length, values.length); i++) {
				if (values[i].isEmpty()) continue;
				result.data.put(keys[i], values[i].intern());
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
		String[] keys = splitCSV(firstLine);
		for (int i = 0; i < keys.length; i++) {
			keys[i] = keys[i].trim().intern();
		}
		while (s.hasNextLine()) {
			String[] values = splitCSV(s.nextLine());
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
			new File("public/trip-blocks/").mkdirs();
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
		HashMap<String, Trip> trips = parseData(tripScanner, "trip_id", () -> new Trip(routes));
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
		Arrays.sort(routeArray, Comparator.comparing((Function<Route, String>) r -> r.data.getOrDefault("route_short_name", "")));
		for (Route r : routeArray) {
			String backColor = onlyIf(r.data.get("route_color"), t -> t.matches("[0-9a-fA-F]{6}"));
			String foreColor = onlyIf(r.data.get("route_text_color"), t -> t.matches("[0-9a-fA-F]{6}"));
			if (backColor == null) backColor = "ffffff";
			if (foreColor == null) foreColor = "000000";
			index.print(String.format("<li><span style=\"background-color: #%s; color: #%s;\">", backColor, foreColor));
			index.format("%s</span> (<a href=\"routes/%s.html\">View stops</a>)</li>", 
					sanitizeHtml(r.getFullName(), true), getHtmlFileName(r.data.get("route_id")));
		}
		index.format("</ul><p>Last Updated: %s</p>", Instant.now());
		index.close();
		/* Generate a list of unique trips for each route. Each trip is unique if
		it has the same set of stops. */
		AtomicInteger nTripsI = new AtomicInteger();
		trips.values().parallelStream().sorted(Comparator.comparingInt(System::identityHashCode)).forEach(t -> {
			int nTrips = nTripsI.getAndIncrement();
			if (nTrips % 500 == 0) {
				Runtime.getRuntime().gc();
				System.out.format("Processing %d trips...", nTrips);
			}
			Route tripRoute = t.routeId;
			if (tripRoute == null) {
				return;
			}
			boolean found = false;
			synchronized (tripRoute.uniqueTrips) {
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
		});
		routes.values().parallelStream().forEach(r -> {
			/* STEP 1: static route html files */
			String commonHtmlName = getHtmlFileName(r.data.get("route_id"));
			PrintStream routeData = new PrintStream(new BufferedOutputStream(openTextOrDie(String.format("public/routes/%s.html", commonHtmlName))));
			System.out.print("Processing " + r.getLongName() + "... ");
			routeData.println("<!DOCTYPE html>\n<html><head><title>");
			routeData.print(sanitizeHtml(r.getFullName(), true));
			routeData.print("</title></head><body><h1>");
			routeData.print(sanitizeHtml(r.getFullName(), true));
			routeData.print("</h1>");
			for (UniqueTripSet s : r.uniqueTrips) {
				routeData.print(String.format("<h2>%s</h2>", sanitizeHtml(s.data.getOrDefault("trip_headsign", ""), true)));
				routeData.print("<table border=1>");
				for (StopTime st : s.stopList) {
					/* Indicate in the Stop object that it is part of this route (r) */
					synchronized (st.stopId) {
						st.stopId.servicedRoutes.add(r);
					}
					routeData.print("<tr><td style=\"white-space: nowrap;\">");
					routeData.print(sanitizeHtml(st.stopId.data.get("stop_name"), true));
					routeData.print("</td>");
					Trip lastTrip = null;
					for (Trip t : s.trips) {
						StopTime indivTime = t.stopTimes.stream().filter(t1 -> t1.stopSequence == st.stopSequence).findAny().orElse(null);
						if (indivTime == null) continue;
						StringBuilder sb = new StringBuilder();
						/* Eliminate duplicate trips */
						if (Trip.compress(t, lastTrip) != 0) {
							lastTrip = t;
						} else {
							continue;
						}
						/*
						/ Eliminate duplicate arrival times /
						if (indivTime.arrivalTime != lastArrivalTime) {
							lastArrivalTime = indivTime.arrivalTime;
						} else {
							continue;
						}
						*/
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
		});
		/* STEP 3: stops database */
		System.out.print("Generating stops.json... ");
		PrintStream stopDB = openTextOrDie("public/meta/stops.json");
		stopDB.print("{");
		stopDB.print(stops.values().parallelStream().map(Stop::jsonStringifyMetadata)
				.sorted(Comparator.naturalOrder()).collect(Collectors.joining(",\n")));
		stopDB.print("}");
		stopDB.close();
		System.out.print("done.\nGenerating service.json... ");
		PrintStream serviceDB = openTextOrDie("public/meta/service.json");
		serviceDB.print("{");
		serviceDB.print(g.values().parallelStream().map(ServiceGroup::jsonStringify).collect(Collectors.joining(",\n")));
		serviceDB.print("}");
		serviceDB.close();
		System.out.print("done.\nGenerating routes.json... ");
		/* STEP 4: routes database */
		PrintStream routeDB = openTextOrDie("public/meta/routes.json");
		routeDB.print("[");
		Arrays.sort(routeArray, Comparator.comparingInt(Route::getNumber));
		for (int i = 0; i < routeArray.length; i++) {
			if (routeArray[i].getNumber() != i) {
				System.out.println("error!");
				throw new RuntimeException();
			}
			routeDB.print(routeArray[i].jsonStringify());
			routeDB.print(',');
		}
		routeDB.print("{}]");
		routeDB.close();
		System.out.println("done.");
		/* STEP 5: trip block database */
		TreeMap<String, SortedSet<Trip>> tripsByBlock = trips.values().parallelStream()
			.collect(Collectors.groupingBy(b -> b.data.getOrDefault("block_id", ""),
			TreeMap<String, SortedSet<Trip>>::new,
			Collectors.toCollection(() -> new TreeSet<Trip>(UniqueTripSet::compareTripsByArrival))));
		int fileSizeLimit = 16384;
		LinkedList<String> tailOfAll = new LinkedList<>();
		Iterator<Map.Entry<String, SortedSet<Trip>>> entries = tripsByBlock.entrySet().iterator();
		for (int i = 0; entries.hasNext(); i++) {
			PrintStream tripBlockBucket = openTextOrDie(String.format("public/trip-blocks/%d.json", i));
			tripBlockBucket.print("{");
			boolean isFirstEntry = true;
			String tailEntry = null;
			for (int j = 0; j < fileSizeLimit; ) {
				if (!entries.hasNext()) break;
				Map.Entry<String, SortedSet<Trip>> e = entries.next();
				if (!isFirstEntry) tripBlockBucket.print(',');
				isFirstEntry = false;
				tripBlockBucket.format("\"%s\":", e.getKey());
				Route initialRoute = null;
				Stream.Builder<String> b = Stream.builder();
				for (Trip t : e.getValue()) {
					Route tripRoute = t.routeId;
					if (tripRoute != initialRoute) {
						initialRoute = tripRoute;
						b.add(String.format("\"!%s\"", quote(tripRoute.getPrimaryValue())));
					}
					b.add(String.format("\"%d+%s\"", t.indexInJsonArray, quote(t.getPrimaryValue())));
				}
				String result = Arrays.toString(b.build().toArray());
				j += result.length();
				tripBlockBucket.print(result);
				tailEntry = e.getKey();
			}
			if (tailEntry == null) {
				throw new RuntimeException();
			} else {
				tailOfAll.add("\"" + quote(tailEntry) + "\"");
			}
			tripBlockBucket.print("}");
			tripBlockBucket.close();
		}
		/* STEP 6: metadata */
		PrintStream metadataDB = openTextOrDie("public/meta/meta.json");
		metadataDB.format("{\"tripBlockLimits\":%s}", Arrays.toString(tailOfAll.toArray()));
		metadataDB.close();
	}

	private static void parseCalendar(Map<String,ServiceGroup> g, Scanner calendarDateScanner) {
		String keys = calendarDateScanner.nextLine();
		String[] kl = splitCSV(keys);
		while (calendarDateScanner.hasNextLine()) {
			String values = calendarDateScanner.nextLine();
			LocalDate exceptionDate = null;
			int exceptionVal = 0;
			String serviceId = null;
			String[] vl = splitCSV(values);
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
			if (rsg == null) {
				rsg = new ServiceGroup();
				rsg.data.put("start_date", "20181225");
				rsg.data.put("end_date", "20191231");
				rsg.data.put("service_id", serviceId);
				rsg.complete();
				g.put(serviceId, rsg);
			}
			if (exceptionVal != 0 && rsg != null) {
				rsg.exceptions.put(exceptionDate, exceptionVal == 1 ? "add" : "remove");
			}
		}
	}
}
