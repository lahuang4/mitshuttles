package edu.mit.web.lahuang4.mitshuttles;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.SimpleXmlConverterFactory;

public class ShuttleList extends AppCompatActivity {

    private static final String TAG = "ShuttleList";

    private boolean configured = false;

    private static final String MIT = "mit";
    private static final String CHARLES_RIVER = "charles-river";
    private static final String MBTA = "mbta";

    private static int numAgencies;

    private String[] daytimeShuttleNames = {
            "Kendall to Charles Park",
            "Tech Shuttle",
            "Boston Daytime",
            "EZRide - Morning",
            "EZRide - Midday"
    };
    private String[] nighttimeShuttleNames = {
            "Boston East",
            "Boston West",
            "Somerville",
            "Campus Shuttle",
            "EZRide - Evening"
    };
    private String[] otherShuttleNames = {
            "1 Bus",
            "Trader Joe's - Whole Foods"
    };

    private Retrofit retrofit;
    private NextBus nextBus;
    static Map<String, Route> routes;
    static Map<String, Integer> descriptions;
    static Map<String, String> routeAgencies;

    private int numAgenciesLoaded = 0;
    private static Object numAgenciesLoadedLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the NextBus route configuration.
        routes = new HashMap<>();
        descriptions = new HashMap<>();
        routeAgencies = new HashMap<>();
        descriptions.put("Kendall to Charles Park", R.string.kendchar_text);
        descriptions.put("Tech Shuttle", R.string.tech_text);
        descriptions.put("Boston Daytime", R.string.boston_text);
        descriptions.put("EZRide - Morning", R.string.morning_text);
        descriptions.put("EZRide - Midday", R.string.midday_text);
        descriptions.put("Boston East", R.string.saferidebostone_text);
        descriptions.put("Boston West", R.string.saferidebostonw_text);
        descriptions.put("Somerville", R.string.saferidesomerville_text);
        descriptions.put("Campus Shuttle", R.string.saferidecampshut_text);
        descriptions.put("EZRide - Evening", R.string.evening_text);
        descriptions.put("1 Bus", R.string.one_text);
        descriptions.put("Trader Joe's - Whole Foods", R.string.traderjwf_text);
        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.NEXTBUS_URL)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        nextBus = retrofit.create(NextBus.class);

        setUpList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If we didn't manage to set up successfully previously, try again.
        if (!configured) {
            setUpList();
        }
    }

    private void setUpList() {
        numAgencies = 3;
        getConfig(MIT);
        getConfig(CHARLES_RIVER);
        // We get just the 1 bus route for MBTA, since requesting all routes will exceed the limit
        // for NextBus api responses
        getConfig(MBTA, "1");
    }

    @Root(name = "body")
    public static class ConfigBody {
        @Attribute
        String copyright;

        @ElementList(inline = true)
        List<Route> routes;
    }

    @Root(name = "route", strict = false)
    public static class Route {
        @ElementList(inline = true)
        List<Stop> stops;

        @ElementList(inline = true)
        List<Direction> directions;

        @Attribute
        String tag;

        @Attribute
        String title;

        @Attribute
        String color;

        @Attribute
        String oppositeColor;

        @Attribute
        String latMin;

        @Attribute
        String latMax;

        @Attribute
        String lonMin;

        @Attribute
        String lonMax;
    }

    @Root(name = "direction", strict = false)
    public static class Direction {
        @ElementList(inline = true)
        List<DirStop> stops;

        @Attribute
        String tag;

        @Attribute
        String title;

        @Attribute
        String name;
    }

    @Root(name = "stop", strict = false)
    public static class Stop {
        @Attribute
        String tag;

        @Attribute
        String title;

        @Attribute
        String lat;

        @Attribute
        String lon;

        String dir;
    }

    @Root(name = "stop")
    public static class DirStop {
        @Attribute
        String tag;
    }

    private void getConfig(final String agency) {
        Call<ConfigBody> call = nextBus.getConfig("routeConfig", agency);
        getRouteInformation(agency, call);
    }

    private void getConfig(final String agency, String route) {
        Call<ConfigBody> call = nextBus.getConfig("routeConfig", agency, route);
        getRouteInformation(agency, call);
    }

    private void getRouteInformation(final String agency, Call<ConfigBody> call) {
        call.enqueue(new Callback<ConfigBody>() {
            @Override
            public void onResponse(Response<ConfigBody> response) {
                List<Route> routeList = response.body().routes;
                for (Route r : routeList) {
                    Log.d(TAG, "Adding route " + r.title);
                    // Make some titles look nicer
                    if (r.title.contains("Saferide")) {
                        r.title = r.title.substring("Saferide ".length());
                    }
                    if (r.title.equals("1")) {
                        r.title = "1 Bus";
                    }
                    if (agency.equals(CHARLES_RIVER)) {
                        r.title = "EZRide - " + r.title;
                    }
                    routes.put(r.title, r);
                    routeAgencies.put(r.title, agency);

                    // Include direction information in the stops
                    Map<String, String> stopDirs = new HashMap<>();
                    for (Direction dir : r.directions) {
                        for (DirStop stop : dir.stops) {
                            stopDirs.put(stop.tag, dir.tag);
                        }
                    }
                    for (Stop stop : r.stops) {
                        stop.dir = stopDirs.get(stop.tag);
                    }
                }
                configured = true;
                for (String s : routes.keySet()) {
                    String stopStr = "";
                    for (Stop stop : routes.get(s).stops) {
                        stopStr += stop.tag + ", ";
                    }
                    Log.d(TAG, s + ": " + stopStr);
                }
                synchronized (numAgenciesLoadedLock) {
                    numAgenciesLoaded++;
                    if (numAgenciesLoaded == numAgencies) {
                        Log.d(TAG, "Assembled config.");
                        // All agencies loaded, build shuttle list
                        buildShuttleList();
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                setContentView(R.layout.network_error_message);
            }
        });
    }

    private void buildShuttleList() {
        // TODO: Populate menu based on NextBus config.
        // Populate list with shuttles.
        SeparatedListAdapter adapter = new SeparatedListAdapter(this);
        adapter.addSection("Daytime Shuttles:", new ArrayAdapter<>(this, R.layout.list_item,
                daytimeShuttleNames));
        adapter.addSection("Nighttime Shuttles:", new ArrayAdapter(this,
                R.layout.list_item, nighttimeShuttleNames));
        adapter.addSection("Other Shuttles:", new ArrayAdapter(this,
                R.layout.list_item, otherShuttleNames));

        setContentView(R.layout.activity_shuttle_list);
        ListView shuttleListView = (ListView)findViewById(R.id.shuttle_list);
        shuttleListView.setAdapter(adapter);

        shuttleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String value = (String) parent.getItemAtPosition(position);
                Intent intent = new Intent(parent.getContext(), ShuttleSchedule.class);
                intent.putExtra("Agency", routeAgencies.get(value));
                intent.putExtra("Route", value);
                startActivity(intent);
            }
        });
    }

}
