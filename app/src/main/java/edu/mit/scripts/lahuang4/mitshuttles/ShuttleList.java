package edu.mit.scripts.lahuang4.mitshuttles;

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

    private String[] daytimeShuttleNames = {
            "Kendall to Charles Park",
            "Tech Shuttle",
            "EZRide - Evening",
            "EZRide - Midday",
            "EZRide - Morning"
    };
    private String[] nighttimeShuttleNames = {
            "Boston East",
            "Boston West",
            "Somerville",
            "Campus Shuttle"
    };

    private Retrofit retrofit;
    private NextBus nextBus;
    static Map<String, Route> routes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the NextBus route configuration.
        routes = new HashMap<>();
        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.NEXTBUS_URL)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        nextBus = retrofit.create(NextBus.class);

        getConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If we didn't manage to set up successfully previously, try again.
        if (!configured) {
            getConfig();
        }
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
    }

    // TODO: Get stop names for each route.
    private void getConfig() {
        Call<ConfigBody> call = nextBus.getConfig("routeConfig", "mit");
        call.enqueue(new Callback<ConfigBody>() {
            @Override
            public void onResponse(Response<ConfigBody> response) {
                List<Route> routeList = response.body().routes;
                for (Route r : routeList) {
                    Log.d(TAG, "Adding route " + r.title);
                    if (r.title.contains("Saferide")) {
                        r.title = r.title.substring("Saferide ".length());
                    }
                    routes.put(r.title, r);
                }
                Log.d(TAG, "Assembled config.");
                configured = true;
                for (String s : routes.keySet()) {
                    String stops = "";
                    for (Stop stop : routes.get(s).stops) {
                        stops += stop.tag + ", ";
                    }
                    Log.d(TAG, s + ": " + stops);
                }
                buildShuttleList();
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
        adapter.addSection("Nighttime Saferide Shuttles:", new ArrayAdapter(this,
                R.layout.list_item, nighttimeShuttleNames));

        setContentView(R.layout.activity_shuttle_list);
        ListView shuttleListView = (ListView)findViewById(R.id.shuttle_list);
        shuttleListView.setAdapter(adapter);

        shuttleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String value = (String) parent.getItemAtPosition(position);
                Intent intent = new Intent(parent.getContext(), ShuttleSchedule.class);
                intent.putExtra("Route", value);
                startActivity(intent);
            }
        });
    }

}
