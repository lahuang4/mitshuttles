package edu.mit.web.lahuang4.mitshuttles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

public class ShuttleSchedule extends AppCompatActivity {

    private static final String TAG = "ShuttleSchedule";

    private static Context context;
    private static ListView shuttleStopList;

    private String agency, routeName;
    private ShuttleList.Route route;
    private Retrofit retrofit;
    private NextBus nextBus;
    private ScheduledExecutorService refresher;
    private ScheduledFuture scheduledFuture;
    private List<Map<String, ?>> adapterStops = new ArrayList<>();
    private SimpleAdapter listAdapter;

    private static final int REFRESH_INTERVAL = 10;
    private static final String ITEM_ICON = "icon";
    private static final String ITEM_LEFT = "left";
    private static final String ITEM_RIGHT = "right";
    private static final String ITEM_RIGHT_SELECT = "right_select";

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;
        agency = getIntent().getStringExtra("Agency");
        routeName = getIntent().getStringExtra("Route");
        route = ShuttleList.routes.get(routeName);
        setContentView(R.layout.activity_shuttle_schedule);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        TextView shuttleScheduleName = (TextView)findViewById(R.id.shuttle_schedule_text_name);
        shuttleScheduleName.setText(routeName);
        TextView shuttleScheduleText = (TextView)findViewById(R.id.shuttle_schedule_text);
        shuttleScheduleText.setText(getString(ShuttleList.descriptions.get(routeName)));
        shuttleStopList = (ListView)findViewById(R.id.shuttle_stops);

        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.NEXTBUS_URL)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        nextBus = retrofit.create(NextBus.class);

        refresher = Executors.newScheduledThreadPool(1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.schedule_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_map) {
            Intent intent = new Intent(context, ShuttleMap.class);
            intent.putExtra("Agency", agency);
            intent.putExtra("Route Tag", route.tag);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (route.title.equals("1 Bus")) {
            scheduledFuture = refresher.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    filter1BusStops();
                    refreshShuttleSchedule();
                }
            }, 0, REFRESH_INTERVAL, TimeUnit.SECONDS);
        } else {
            scheduledFuture = refresher.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    refreshShuttleSchedule();
                }
            }, 0, REFRESH_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refresher.shutdown();
    }

    @Root(name = "body")
    public static class PredictionBody {
        @Attribute
        String copyright;

        @ElementList(inline = true)
        List<Schedule> schedule;
    }

    @Root(name = "predictions", strict = false)
    public static class Schedule {
        @Element(name = "direction", required = false)
        Direction direction;

        @ElementList(inline = true, required = false)
        List<Message> message;

        @Attribute
        String agencyTitle;

        @Attribute
        String routeTitle;

        @Attribute
        String routeTag;

        @Attribute
        String stopTitle;

        @Attribute
        String stopTag;

        @Attribute(required = false)
        String dirTitleBecauseNoPredictions;
    }

    @Root(name = "direction")
    public static class Direction {
        @ElementList(inline = true)
        List<Prediction> predictions;

        @Attribute
        String title;
    }

    @Root(name = "message")
    public static class Message {
        @Attribute
        String text;

        @Attribute
        String priority;
    }

    @Root(name = "prediction", strict = false)
    public static class Prediction {
        @Attribute
        String epochTime;

        @Attribute
        private String seconds;

        @Attribute
        private String minutes;

        @Attribute(required = false)
        private String isDeparture;

        @Attribute(required = false)
        String affectedByLayover;

        @Attribute
        String dirTag;

        @Attribute
        String vehicle;

        @Attribute
        String block;

        public int getSeconds() {
            return Integer.parseInt(seconds);
        }

        public int getMinutes() {
            return Integer.parseInt(minutes);
        }

        public String getDirTag() {
            return dirTag;
        }
    }

    private void refreshShuttleSchedule() {
        Log.d(TAG, "Getting schedule for " + route.tag);
        // Compile stops for request
        List<String> stops = new ArrayList<>();
        for (ShuttleList.Stop stop : route.stops) {
            // Make some stop names look nicer
            if (stop.title.equals("Simmons hall")) {
                stop.title = "Simmons Hall";
            }
            stop.title = stop.title.replaceAll("Massachusetts", "Mass");
            stop.title = stop.title.replaceAll("\\(Arrival\\)", "");
            stops.add(route.tag + "|" + stop.tag);
        }
        getPredictionsForStops(stops);
    }

    private void filter1BusStops() {
        Log.d(TAG, "Getting schedule for " + route.tag);
        // Keep only the most relevant 1 Bus stops
        Map<String, String> stopNames = new HashMap<>();
        stopNames.put("97", "77 Mass Ave Northbound");
        stopNames.put("75", "77 Mass Ave Southbound");
        stopNames.put("93", "Hynes Conv Ctr Northbound");
        stopNames.put("79", "Hynes Conv Ctr Southbound");
        stopNames.put("110", "Harvard Square");
        stopNames.put("66", "Mt Auburn St @ DeWolfe St Southbound");
        stopNames.put("102", "Central Square Northbound");
        stopNames.put("72", "Central Square Southbound");
        List<String> stopTags = Arrays.asList("97", "75", "93", "79", "110", "66", "102", "72");
        List<ShuttleList.Stop> filteredStops = new ArrayList<>();
        for (ShuttleList.Stop stop : route.stops) {
            if (stopTags.contains(stop.tag)) {
                stop.title = stopNames.get(stop.tag);
                filteredStops.add(stop);
            }
        }
        route.stops = filteredStops;
    }

    private void getPredictionsForStops(List<String> stops) {
        Call<PredictionBody> call = nextBus.getMultiplePredictions("predictionsForMultiStops",
                agency, stops);
        call.enqueue(new Callback<PredictionBody>() {
            @Override
            public void onResponse(Call<PredictionBody> c, Response<PredictionBody> response) {
                // stopMap stores (stop tag, time of arrival) pairs
                Map<String, String> stopMap = new HashMap<>();
                // stopSeconds stores (stop tag, seconds until arrival) pairs
                Map<String, Integer> stopSeconds = new HashMap<>();
                // stopDirs stores (stop tag, direction tag) pairs
                Map<String, String> stopDirs = new HashMap<>();
                // Fill in stopSeconds with default values in case the NextBus response doesn't
                // return all stops
                for (ShuttleList.Stop stop : route.stops) {
                    stopSeconds.put(stop.tag, Integer.MAX_VALUE);
                }
                for (Schedule schedule : response.body().schedule) {
                    if (schedule.dirTitleBecauseNoPredictions != null) {
                        Log.d(TAG, "Shuttle " + routeName +
                                " is not currently running or NextBus is down.");
                    } else {
                        if (schedule.direction.predictions != null) {
                            for (Prediction p : schedule.direction.predictions) {
                                Log.d(TAG, "Schedule stop prediction: " + p.getMinutes() +
                                        " minutes, or " + p.getSeconds() + " seconds");
                            }
                            stopMap.put(schedule.stopTag,
                                    getETA(schedule.direction.predictions.get(0).getMinutes()));
                            stopSeconds.put(schedule.stopTag,
                                    schedule.direction.predictions.get(0).getSeconds());
                            stopDirs.put(schedule.stopTag,
                                    schedule.direction.predictions.get(0).getDirTag());
                        }
                    }
                }
                adapterStops.clear();
                for (int i = 0; i < route.stops.size(); i++) {
                    ShuttleList.Stop stop = route.stops.get(i);
                    if (stopMap.containsKey(stop.tag)) {
                        adapterStops.add(createItem(isArriving(i, stopSeconds), stop.title,
                                stopMap.get(stop.tag)));
                    } else {
                        adapterStops.add(createItem(false, stop.title, ""));
                    }
                }
                if (listAdapter == null) {
                    listAdapter = new SimpleAdapter(context, adapterStops,
                            R.layout.two_sided_list_item,
                            new String[]{ITEM_ICON, ITEM_LEFT, ITEM_RIGHT, ITEM_RIGHT_SELECT},
                            new int[]{R.id.two_sided_list_icon, R.id.two_sided_list_left,
                                    R.id.two_sided_list_right,
                                    R.id.two_sided_list_right_select});
                    shuttleStopList.setAdapter(listAdapter);
                } else {
                    listAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<PredictionBody> c, Throwable t) {
                t.printStackTrace();
                if (t.getMessage().contains("Element 'Error' does not have a match")) {
                    Log.e(TAG, "Unable to retrieve information for current stop. " +
                            "Are you sure this stop is on the specified route?");
                } else {
                    setContentView(R.layout.network_error_message);
                }
            }
        });
    }

    private Map<String, ?> createItem(boolean arriving, String left, String right) {
        Map<String, String> item = new HashMap<>();
        if (arriving) {
            item.put(ITEM_ICON, Integer.toString(R.drawable.shuttle_green));
            item.put(ITEM_RIGHT_SELECT, right);
            item.put(ITEM_RIGHT, "");
        } else {
            item.put(ITEM_ICON, Integer.toString(R.drawable.shuttle));
            item.put(ITEM_RIGHT_SELECT, "");
            item.put(ITEM_RIGHT, right);
        }
        item.put(ITEM_LEFT, left);
        return item;
    }

    private String getETA(int minutesUntilArrival) {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, minutesUntilArrival);
        return timeFormat.format(now.getTime());
    }

    private boolean isArriving(int index, Map<String, Integer> stopSeconds) {
        int secondsUntilArrival = stopSeconds.get(route.stops.get(index).tag);
        if (stopSeconds.size() <= 1) {
            return true;
        }
        if (index == 0) {
            if (stopSeconds.containsKey(route.stops.get(route.stops.size()-1).tag) &&
                    stopSeconds.containsKey(route.stops.get(index + 1).tag)) {
                if (stopSeconds.get(route.stops.get(route.stops.size() - 1).tag) >
                        secondsUntilArrival && stopSeconds.get(route.stops.get(index + 1).tag) >
                        secondsUntilArrival) {
                    return true;
                }
            }
            return false;
        }
        if (index == stopSeconds.size()-1) {
            if (stopSeconds.containsKey(route.stops.get(0).tag) &&
                    stopSeconds.containsKey(route.stops.get(index - 1).tag)) {
                if (stopSeconds.get(route.stops.get(0).tag) > secondsUntilArrival &&
                        stopSeconds.get(route.stops.get(index - 1).tag) > secondsUntilArrival) {
                    return true;
                }
            }
            return false;
        }
        if (stopSeconds.containsKey(route.stops.get(index - 1).tag) &&
                stopSeconds.containsKey(route.stops.get(index + 1).tag)) {
            if (stopSeconds.get(route.stops.get(index - 1).tag) > secondsUntilArrival &&
                    stopSeconds.get(route.stops.get(index + 1).tag) > secondsUntilArrival) {
                return true;
            }
        }
        return false;
    }

}
