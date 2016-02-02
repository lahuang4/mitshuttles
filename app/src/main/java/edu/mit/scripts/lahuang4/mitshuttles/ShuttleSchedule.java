package edu.mit.scripts.lahuang4.mitshuttles;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import retrofit2.SimpleXmlConverterFactory;

public class ShuttleSchedule extends AppCompatActivity {

    private static final String TAG = "ShuttleSchedule";

    private static Context context;
    private static ListView shuttleStopList;

    private String routeName;
    private ShuttleList.Route route;
    private Retrofit retrofit;
    private NextBus nextBus;
    private ScheduledExecutorService refresher;
    private ScheduledFuture scheduledFuture;

    private static final int REFRESH_INTERVAL = 10;
    private static final String ITEM_LEFT = "left";
    private static final String ITEM_RIGHT = "right";

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm aa");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        routeName = getIntent().getStringExtra("Route");
        route = ShuttleList.routes.get(routeName);
        setContentView(R.layout.activity_shuttle_schedule);
        TextView shuttleScheduleName = (TextView)findViewById(R.id.shuttle_schedule_text_name);
        shuttleStopList = (ListView)findViewById(R.id.shuttle_stops);
        shuttleScheduleName.setText(routeName);

        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.NEXTBUS_URL)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        nextBus = retrofit.create(NextBus.class);

        refresher = Executors.newScheduledThreadPool(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduledFuture = refresher.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                refreshShuttleSchedule();
            }
        }, 0, REFRESH_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
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

        @ElementList(inline = true)
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

    @Root(name = "prediction")
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
    }

    private void refreshShuttleSchedule() {
        Log.d(TAG, "Getting schedule for " + route.tag);
        List<String> stops = new ArrayList<>();
        for (ShuttleList.Stop stop : route.stops) {
            stops.add(route.tag + "|" + stop.tag);
        }
        Call<PredictionBody> call = nextBus.getMultiplePredictions("predictionsForMultiStops", "mit", stops);
            call.enqueue(new Callback<PredictionBody>() {
                @Override
                public void onResponse(Response<PredictionBody> response) {
                    List<Map<String, ?>> stops = new ArrayList<>();
                    for (Schedule schedule : response.body().schedule) {
                        if (schedule.dirTitleBecauseNoPredictions != null) {
                            Log.d(TAG, "Shuttle " + routeName + " is not currently running or NextBus is down.");
                        } else {
                            for (Message message : schedule.message) {
                                Log.d(TAG, "Schedule message: " + message.text + ", priority " + message.priority);
                            }
                            if (schedule.direction.predictions != null) {
                                for (Prediction p : schedule.direction.predictions) {
                                    Log.d(TAG, "Schedule stop prediction: " + p.getMinutes() +
                                            " minutes, or " + p.getSeconds() + " seconds");
                                }
                                stops.add(createItem(schedule.stopTitle,
                                        getETA(schedule.direction.predictions.get(0).getMinutes())));
                            }
                        }
                    }
                    shuttleStopList.setAdapter(new SimpleAdapter(context, stops,
                            R.layout.two_sided_list_item, new String[] { ITEM_LEFT, ITEM_RIGHT },
                            new int[] { R.id.two_sided_list_left, R.id.two_sided_list_right }));
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                    if (t.getMessage().contains("Element 'Error' does not have a match")) {
                        Log.e(TAG, "Unable to retrieve information for current stop. Are you sure this stop is on the specified route?");
                    } else {
                        setContentView(R.layout.network_error_message);
                    }
                }
            });
    }

    private Map<String, ?> createItem(String left, String right) {
        Map<String, String> item = new HashMap<>();
        item.put(ITEM_LEFT, left);
        item.put(ITEM_RIGHT, right);
        return item;
    }

    private String getETA(int minutesUntilArrival) {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, minutesUntilArrival);
        return timeFormat.format(now.getTime());
    }

}
