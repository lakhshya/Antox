package im.tox.antox.activities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.app.ActionBar;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import im.tox.antox.data.AntoxDB;
import im.tox.antox.utils.AntoxFriend;
import im.tox.antox.fragments.ChatFragment;
import im.tox.antox.utils.Constants;
import im.tox.antox.fragments.ContactsFragment;
import im.tox.antox.utils.DhtNode;
import im.tox.antox.utils.Friend;
import im.tox.antox.utils.FriendRequest;
import im.tox.antox.adapters.LeftPaneAdapter;
import im.tox.antox.utils.LeftPaneItem;
import im.tox.antox.utils.Message;
import im.tox.antox.R;
import im.tox.antox.tox.ToxDoService;
import im.tox.antox.tox.ToxService;
import im.tox.antox.tox.ToxSingleton;
import im.tox.antox.utils.UserDetails;
import im.tox.jtoxcore.ToxUserStatus;

/**
 * The Main Activity which is launched when the app icon is pressed in the app tray and acts as the
 * central part of the entire app. It also displays the friends list to the user.
 *
 * @author Mark Winter (Astonex)
 */

public class MainActivity extends ActionBarActivity{

    private static final String TAG = "im.tox.antox.activities.MainActivity";


    private Intent startToxIntent;

    public LeftPaneAdapter leftPaneAdapter;


    public SlidingPaneLayout pane;
    public ChatFragment chat;
    private ContactsFragment contacts;
    private IntentFilter filter;
    private boolean tempRightPaneActive;
    MenuItem ag;

    /**
     * Stores all friend details and used by the contactsAdapter for displaying
     */
    public String activeTitle = "Antox";


    public ArrayList<String> leftPaneKeyList;

    private final ToxSingleton toxSingleton = ToxSingleton.getInstance();

    public ArrayList<Friend> friendList;
    private PaneListener paneListener;

    /*
     * Allows menu to be accessed from menu unrelated subroutines such as the pane opened
     */
    private Menu menu;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            String action = intent.getStringExtra("action");
            if (action != null) {
                Log.d(TAG, "action: " + action);
                if (action.equals(Constants.FRIEND_REQUEST)) {

                } else if (action.equals(Constants.UPDATE_LEFT_PANE)) {
                    updateLeftPane();
                } else if (action.equals(Constants.REJECT_FRIEND_REQUEST)) {
                    updateLeftPane();
                    Context ctx = getApplicationContext();
                    String text = getString(R.string.friendrequest_deleted);
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(ctx, text, duration);
                    toast.show();
                } else if (action.equals(Constants.UPDATE_MESSAGES)) {
                    Log.d(TAG, "UPDATE_MESSAGES, intent key = " + intent.getStringExtra("key") + ", activeFriendKey = " + toxSingleton.activeFriendKey);

                    updateLeftPane();
                    if (intent.getStringExtra("key").equals(toxSingleton.activeFriendKey)) {
                        updateChat(toxSingleton.activeFriendKey);
                    }
                } else if (action.equals(Constants.ACCEPT_FRIEND_REQUEST)) {
                    updateLeftPane();
                    Context ctx = getApplicationContext();
                    String text = getString(R.string.friendrequest_accepted);
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(ctx, text, duration);
                    toast.show();
                } else if (action.equals(Constants.FRIEND_LIST)) {

                } else if (action.equals(Constants.UPDATE)) {
                    updateLeftPane();
                    if (toxSingleton.rightPaneActive) {
                        activeTitle = toxSingleton.friendsList.getById(toxSingleton.activeFriendKey).getName();
                        setTitle(activeTitle);
                    }
                }
            }
        }
    };


    public void updateChat(String key) {
        Log.d(TAG, "updating chat");
        if(toxSingleton.friendsList.getById(key)!=null
                && toxSingleton.friendsList.getById(key).getName()!=null ){
            AntoxDB db = new AntoxDB(this);
            if (toxSingleton.rightPaneActive) {
                db.markIncomingMessagesRead(key);
            }
            try {
                chat.updateChat(db.getMessageList(key));
                db.close();
                updateLeftPane();
            } catch (NullPointerException e) {
                e.printStackTrace();
                Log.d(TAG, e.toString());
            }
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        if (i.getAction() != null) {
            if (i.getAction().equals(Constants.SWITCH_TO_FRIEND) && toxSingleton.friendsList.getById(i.getStringExtra("key")) != null) {
                String key = i.getStringExtra("key");
                String name = i.getStringExtra("name");
                Fragment newFragment = new ChatFragment();
                toxSingleton.activeFriendKey = key;
                toxSingleton.activeFriendRequestKey = null;
                tempRightPaneActive = true;
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.right_pane, newFragment);
                transaction.addToBackStack(null);
                transaction.commit();
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                setTitle(activeTitle);
                clearUselessNotifications();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Fix for an android 4.1.x bug */
        if(Build.VERSION.SDK_INT != Build.VERSION_CODES.JELLY_BEAN
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
        }

        /* Check if first time ever running by checking the preferences */
        SharedPreferences pref = getSharedPreferences("main",
                Context.MODE_PRIVATE);

        SharedPreferences settings = getSharedPreferences("settings", Context.MODE_PRIVATE);

        // If beenLoaded is 0, then never been run
        if (pref.getInt("beenLoaded", 0) == 0) {
            // Launch welcome activity which will run the user through initial
            // settings
            // and give a brief description of antox
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivityForResult(intent, Constants.WELCOME_ACTIVITY_REQUEST_CODE);
        }

        // Checks to see if a language is set in settings
        SharedPreferences settingsPref = getSharedPreferences("settings", Context.MODE_PRIVATE);
        String language = settingsPref.getString("language", "");
        // If it has not, set it based on phone locale
        if (language.equals("")){
            SharedPreferences.Editor editor = settingsPref.edit();
            editor.putString("language", getCurrentLanguageOnStart());
            editor.commit();
        }
        // Otherwise, check which language has been selected and set it based on that
        else{
            Locale locale = null;
            switch (language) {
                case "English":
                    locale = new Locale("en");
                    break;
                case "Deutsch":
                    locale = new Locale("de");
                    break;
                case "Español":
                    locale = new Locale("es");
                    break;
                case "Français":
                    locale = new Locale("fr");
                    break;
                case "Italiano":
                    locale = new Locale("it");
                    break;
                case "Nederlands":
                    locale = new Locale("nl");
                    break;
                case "Polski":
                    locale = new Locale("pl");
                    break;
                case "Svenska":
                    locale = new Locale("sv");
                    break;
                case "Türkçe":
                    locale = new Locale("tr");
                    break;
                case "Русский":
                    locale = new Locale("ru");
                    break;
                case "Український":
                    locale = new Locale("uk");
                    break;
                default:
                    break;
            }
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getApplicationContext().getResources().updateConfiguration(config, getApplicationContext().getResources().getDisplayMetrics());
        }

        toxSingleton.activeFriendKey=null;
        toxSingleton.activeFriendRequestKey=null;
        toxSingleton.leftPaneActive = true;

        /* Check if connected to the Internet */
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
        {
            // Executes in a separate thread so UI experience isn't affected
            // Downloads the DHT node details
            if(DhtNode.ipv4.size() == 0)
                new DHTNodeDetails().execute();
        }
        else {
            showAlertDialog(MainActivity.this, getString(R.string.main_no_internet),
                    getString(R.string.main_not_connected));
        }

        /* If the tox service isn't already running, start it */
        if(!isToxServiceRunning()) {
            /* If the service wasn't running then we wouldn't have gotten callbacks for a user
            *  going offline so default everyone to offline and just wait for callbacks.
            */
            AntoxDB db = new AntoxDB(getApplicationContext());
            db.setAllOffline();
            db.close();

            startToxIntent = new Intent(this, ToxDoService.class);
            startToxIntent.setAction(Constants.START_TOX);
            this.startService(startToxIntent);

        }

        final ArrayList<String> groups = new ArrayList<String>();
        Collections.addAll(groups, getResources().getStringArray(R.array.actions));
        groups.add(getResources().getString(R.string.manage_groups_friends));
        SharedPreferences sharedPreferences = getSharedPreferences("groups", Context.MODE_PRIVATE);
        if (!sharedPreferences.getAll().isEmpty()) {
            Map<String, ?> keys = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                groups.add(entry.getValue().toString());
            }
        }

        SpinnerAdapter adapter = new ArrayAdapter<String>(this, R.layout.group_item, groups);
        if (settingsPref.getInt("group_option", -1) == -1) {
            SharedPreferences.Editor editor = settingsPref.edit();
            editor.putInt("group_option", 0);
            editor.commit();
        }

        ActionBar.OnNavigationListener callback = new ActionBar.OnNavigationListener() {
            //String[] items = getResources().getStringArray(R.array.actions);
            @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                Log.d("NavigationItemSelected", groups.get(itemPosition));
                SharedPreferences settingsPref = getSharedPreferences("settings", Context.MODE_PRIVATE);
                if (itemPosition != settingsPref.getInt("group_option", -1)) {
                    SharedPreferences.Editor editor = settingsPref.edit();
                    editor.putInt("group_option", itemPosition);
                    editor.putString("group_option_name", groups.get(itemPosition));
                    editor.commit();
                    updateLeftPane();
                }
                return true;
            }
        };
        ActionBar actions = getSupportActionBar();
        actions.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actions.setDisplayShowTitleEnabled(false);
        actions.setListNavigationCallbacks(adapter, callback);
        actions.setSelectedNavigationItem(settingsPref.getInt("group_option", 0));

        Intent getFriendsList = new Intent(this, ToxService.class);
        getFriendsList.setAction(Constants.FRIEND_LIST);
        this.startService(getFriendsList);

        UserDetails.note = settingsPref.getString("saved_note_hint", "");

        pane = (SlidingPaneLayout) findViewById(R.id.slidingpane_layout);
        paneListener = new PaneListener();
        pane.setPanelSlideListener(paneListener);
        pane.openPane();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            getSupportActionBar().setIcon(R.drawable.ic_actionbar);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        contacts = (ContactsFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_contacts);

        updateLeftPane();
        onNewIntent(getIntent());
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    private String getCurrentLanguageOnStart() {
        String currentLanguage = getResources().getConfiguration().locale.getCountry().toLowerCase();
        String language;
        switch (currentLanguage) {
            case "en":
                language = "English";
                break;
            case "de":
                language = "Deutsch";
                break;
            case "es":
                language = "Español";
                break;
            case "fr":
                language = "Français";
                break;
            case "it":
                language = "Italiano";
                break;
            case "nl":
                language = "Nederlands";
                break;
            case "pl":
                language = "Polski";
                break;
            case "sv":
                language = "Svenska";
                break;
            case "tr":
                language = "Türkçe";
                break;
            default:
                language = "English";
        }
        return language;
    }
    private Message mostRecentMessage(String key, ArrayList<Message> messages) {
        for (int i=0; i<messages.size(); i++) {
            if (key.equals(messages.get(i).key)) {
                return messages.get(i);
            }
        }
        return new Message(-1, key, "", false, true, true, true, new Timestamp(0,0,0,0,0,0,0));
    }

    private int countUnreadMessages(String key, ArrayList<Message> messages) {
        int counter = 0;
        if(key!=null) {
            Message m;
            for (int i = 0; i < messages.size(); i++) {
                m = messages.get(i);
                if (m.key.equals(key) && !m.is_outgoing) {
                    if (!m.has_been_read) {
                        counter += 1;
                    } else {
                        return counter;
                    }
                }
            }
        }
        return counter;
    }

    public void updateGroupsList() {
        SharedPreferences settingsPref = getSharedPreferences("settings", Context.MODE_PRIVATE);

        ArrayList<String> groups = new ArrayList<String>();
        Collections.addAll(groups, getResources().getStringArray(R.array.actions));
        groups.add(getResources().getString(R.string.manage_groups_friends));
        SharedPreferences sharedPreferences = getSharedPreferences("groups", Context.MODE_PRIVATE);
        if (!sharedPreferences.getAll().isEmpty()) {
            Map<String, ?> keys = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                groups.add(entry.getValue().toString());
            }
        }
        SpinnerAdapter adapter = new ArrayAdapter<String>(this, R.layout.group_item, groups);

        if (settingsPref.contains("group_option_name")) {
            //check if the group previous selected still exists
            boolean ok = false;
            int pos = -1;
            for (String group : groups) {
                if (group.equals(settingsPref.getString("group_option_name", "Default"))) {
                    ok = true;
                    pos = groups.indexOf(group);
                    break;
                }
            }

            SharedPreferences.Editor editor = settingsPref.edit();
            if (ok) {
                editor.putInt("group_option", pos);
                editor.commit();
            }
            else {
                editor.putInt("group_option", 0);
                editor.putString("group_option_name", "All");
                editor.commit();
            }
        }
        else {
            SharedPreferences.Editor editor = settingsPref.edit();
            editor.putInt("group_option", 0);
            editor.putString("group_option_name", "All");
            editor.commit();
        }

        final ArrayList<String> groupsClone = groups;

        ActionBar.OnNavigationListener callback = new ActionBar.OnNavigationListener() {
            @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                Log.d("NavigationItemSelected", groupsClone.get(itemPosition));
                SharedPreferences settingsPref = getSharedPreferences("settings", Context.MODE_PRIVATE);
                if (itemPosition != settingsPref.getInt("group_option", -1)) {
                    SharedPreferences.Editor editor = settingsPref.edit();
                    editor.putInt("group_option", itemPosition);
                    editor.putString("group_option_name", groupsClone.get(itemPosition));
                    editor.commit();
                    updateLeftPane();
                }
                return true;
            }
        };
        ActionBar actions = getSupportActionBar();
        actions.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actions.setListNavigationCallbacks(adapter, callback);
        actions.setSelectedNavigationItem(settingsPref.getInt("group_option", 0));

        if (toxSingleton.rightPaneActive) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            setTitle(activeTitle);

            // Hide add friend icon
            MenuItem af = menu.findItem(R.id.add_friend);
            MenuItemCompat.setShowAsAction(af,MenuItem.SHOW_AS_ACTION_NEVER);

            //Hide group menu
            ActionBar bar = MainActivity.this.getSupportActionBar();
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

            // Hide search icon
            MenuItem search = menu.findItem(R.id.search_friend);
            MenuItemCompat.setShowAsAction(search, MenuItem.SHOW_AS_ACTION_NEVER);

            //ag.setVisible(true);
            /* Hide until functionality is implemented to avoid confusion
            MenuItemCompat.setShowAsAction(ag,MenuItem.SHOW_AS_ACTION_ALWAYS);
            ag.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    addFriendToGroup();
                    return false;
                }
            });
            */
            toxSingleton.rightPaneActive = true;
            toxSingleton.leftPaneActive = false;
            if(toxSingleton.activeFriendKey!=null){
                updateChat(toxSingleton.activeFriendKey);
            }
            clearUselessNotifications();
        }
    }

    public void updateLeftPane() {

        AntoxDB antoxDB = new AntoxDB(this);
        SharedPreferences settingsPref = getSharedPreferences("settings", Context.MODE_PRIVATE);
        int option = settingsPref.getInt("group_option", 0);
        if (option <= 3) {
            friendList = antoxDB.getFriendList(option);
        }
        else {
            friendList = antoxDB.getFriendsInAGroup(settingsPref.getString("group_option_name", "Friends"));
        }

        ArrayList<Message> messageList = antoxDB.getMessageList("");

        Friend friends_list[] = new Friend[friendList.size()];
        friends_list = friendList.toArray(friends_list);

        FriendRequest friend_requests_list[] = new FriendRequest[toxSingleton.friend_requests.size()];
        friend_requests_list = toxSingleton.friend_requests.toArray(friend_requests_list);

        leftPaneAdapter = new LeftPaneAdapter(this);

        leftPaneKeyList = new ArrayList<String>();

        Message msg;

        LinearLayout noFriends = (LinearLayout) findViewById(R.id.left_pane_no_friends);

        if (friend_requests_list.length == 0 && antoxDB.getFriendList(Constants.OPTION_ALL_FRIENDS).size() == 0
                && antoxDB.getFriendList(Constants.OPTION_BLOCKED_FRIENDS).size() == 0) {
            noFriends.setVisibility(View.VISIBLE);
        } else {
            noFriends.setVisibility(View.GONE);
        }

        if (friend_requests_list.length > 0) {
            LeftPaneItem friend_request_header = new LeftPaneItem(Constants.TYPE_HEADER, getResources().getString(R.string.main_friend_requests), null, 0);
            leftPaneAdapter.addItem(friend_request_header);
            leftPaneKeyList.add("");
            for (int i = 0; i < friend_requests_list.length; i++) {
                LeftPaneItem friend_request = new LeftPaneItem(Constants.TYPE_FRIEND_REQUEST, friend_requests_list[i].requestKey, friend_requests_list[i].requestMessage, 0);
                leftPaneAdapter.addItem(friend_request);
                leftPaneKeyList.add(friend_requests_list[i].requestKey);
            }
        }

        if (friends_list.length > 0) {
            if (option > 3) {
                ArrayList<String> groups = new ArrayList<String>();
                groups.add("Friends");
                SharedPreferences sharedPreferences = getSharedPreferences("groups", Context.MODE_PRIVATE);
                if (!sharedPreferences.getAll().isEmpty()) {
                    Map<String, ?> keys = sharedPreferences.getAll();

                    for (Map.Entry<String, ?> entry : keys.entrySet()) {
                        String groupName = entry.getValue().toString();
                        groups.add(groupName);
                    }
                }
                Collections.sort(groups);

                for (String group : groups) {
                    boolean exists = false;
                    for (int i = 0; i < friends_list.length; i++) {
                        if (friends_list[i].friendGroup.equals(group)) {
                            if (exists == false) {
                                LeftPaneItem friends_header;
                                if (group.equals("Friends")) {
                                    friends_header = new LeftPaneItem(Constants.TYPE_HEADER, getResources().getString(R.string.main_friends), null, 0);
                                } else {
                                    friends_header = new LeftPaneItem(Constants.TYPE_HEADER, group, null, 0);
                                }
                                leftPaneAdapter.addItem(friends_header);
                                leftPaneKeyList.add("");
                                exists = true;
                            }
                            msg = mostRecentMessage(friends_list[i].friendKey, messageList);
                            LeftPaneItem friend = new LeftPaneItem(Constants.TYPE_CONTACT, friends_list[i].friendName, msg.message, friends_list[i].icon, countUnreadMessages(friends_list[i].friendKey, messageList), msg.timestamp);
                            leftPaneAdapter.addItem(friend);
                            leftPaneKeyList.add(friends_list[i].friendKey);
                        }
                    }
                }
            }
            else {
                String[] array = getResources().getStringArray(R.array.actions);

                //add the header corresponding to the option: All Online Offline Blocked
                LeftPaneItem friends_header = new LeftPaneItem(Constants.TYPE_HEADER, array[option], null, 0);
                leftPaneAdapter.addItem(friends_header);
                leftPaneKeyList.add("");
                for (int i = 0; i < friends_list.length; i++) {
                    msg = mostRecentMessage(friends_list[i].friendKey, messageList);
                    LeftPaneItem friend = new LeftPaneItem(Constants.TYPE_CONTACT, friends_list[i].friendName, msg.message, friends_list[i].icon, countUnreadMessages(friends_list[i].friendKey, messageList), msg.timestamp);
                    leftPaneAdapter.addItem(friend);
                    leftPaneKeyList.add(friends_list[i].friendKey);
                }
            }
        }
        antoxDB.close();
        contacts.updateLeftPane();
    }


    /**
     * Starts a new intent to open the SettingsActivity class
     *
     * @see im.tox.antox.activities.SettingsActivity
     */
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, Constants.UPDATE_SETTINGS_REQUEST_CODE);
    }
    /**
     * Starts a new intent to open the SettingsActivity class
     *
     * @see im.tox.antox.activities.ProfileActivity
     */
    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
    }
    /**
     * Starts a new intent to open the AboutActivity class
     *
     * @see im.tox.antox.activities.AboutActivity
     */
    private void openAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
    /**
     * Starts a new intent to open the AddFriendActivity class
     *
     * @see im.tox.antox.activities.AddFriendActivity
     */
    private void addFriend() {
        Intent intent = new Intent(this, AddFriendActivity.class);
        startActivityForResult(intent, Constants.ADD_FRIEND_REQUEST_CODE);
    }

    private void openGroupManagement() {
        Intent intent = new Intent(this, ManageGroupsActivity.class);
        startActivity(intent);
    }

    private void clearUselessNotifications () {
        AntoxDB db = new AntoxDB(getApplicationContext());
        if (toxSingleton.rightPaneActive && toxSingleton.activeFriendKey != null
                && toxSingleton.friendsList.all().size() > 0 && !db.isFriendBlocked(toxSingleton.activeFriendKey)) {
            AntoxFriend friend = toxSingleton.friendsList.getById(toxSingleton.activeFriendKey);
            toxSingleton.mNotificationManager.cancel(friend.getFriendnumber());
        }
        db.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        toxSingleton.rightPaneActive = tempRightPaneActive;
        filter = new IntentFilter(Constants.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        if (toxSingleton.activeFriendKey != null) {
            updateChat(toxSingleton.activeFriendKey);
        }
        clearUselessNotifications();
        updateGroupsList();
        updateLeftPane();
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        tempRightPaneActive = toxSingleton.rightPaneActive;
        toxSingleton.rightPaneActive = false;
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        toxSingleton.leftPaneActive = false;
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                openSettings();
                return true;
            case R.id.action_profile:
                openProfile();
                return true;
            case R.id.action_manage_group:
                openGroupManagement();
                return true;
            case R.id.action_about:
                openAbout();
                return true;
            case R.id.add_friend:
                addFriend();
                return true;
            case R.id.search_friend:
                getSupportActionBar().setIcon(R.drawable.ic_actionbar);
                return true;
            case android.R.id.home:
                pane.openPane();
                return true;
            case R.id.action_exit:
                Intent stopToxIntent = new Intent(this, ToxDoService.class);
                stopToxIntent.setAction(Constants.STOP_TOX);
                this.startService(stopToxIntent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addFriendToGroup() {
        Log.v("Add friend to group method", "To implement");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        ag = menu.add(666, 100, 100, R.string.add_to_group);
        ag.setIcon(R.drawable.ic_action_add_group).setVisible(false);
        final MenuItem menuItem = menu.findItem(R.id.search_friend);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuItem);
        searchView
                .setOnQueryTextListener(new SearchView.OnQueryTextListener() {

                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        // do nothing
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        //MainActivity.this.contactsAdapter.getFilter().filter(
                        //        newText);
                        return true;
                    }
                });
        searchView
                .setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {

                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        MenuItemCompat.collapseActionView(menuItem);

                    }
                });
        //the class menu property is now the initialized menu
        this.menu=menu;

        return true;
    }


    /**
     * Method to see if the tox service is already running so it isn't restarted
     */
    private boolean isToxServiceRunning() {
        return toxSingleton.toxStarted;
    }

    public void showAlertDialog(Context context, String title, String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setIcon(R.drawable.ic_launcher);
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        alertDialog.show();
    }


    // Downloads the the first working DHT node
    private class DHTNodeDetails extends AsyncTask<Void, Void, Void> {
        final String[] nodeDetails = new String[7];



        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Connect to the web site
                Document document = Jsoup.connect("http://wiki.tox.im/Nodes").timeout(10000).get();
                Elements nodeRows = document.getElementsByTag("tr");

                for (Element nodeRow : nodeRows) {
                    Elements nodeElements = nodeRow.getElementsByTag("td");
                    int c = 0;
                    for (Element nodeElement : nodeElements)
                        nodeDetails[c++] = nodeElement.text();


                    if (nodeDetails[6] != null && nodeDetails[6].equals("WORK")) {
                        DhtNode.ipv4.add(nodeDetails[0]);
                        DhtNode.ipv6.add(nodeDetails[1]);
                        DhtNode.port.add(nodeDetails[2]);
                        DhtNode.key.add(nodeDetails[3]);
                        DhtNode.owner.add(nodeDetails[4]);
                        DhtNode.location.add(nodeDetails[5]);
                    }
                }
            } catch (UnknownHostException e) {
                // If for some reason website is down, add some known values
                DhtNode.ipv4.add("192.254.75.98");
                DhtNode.ipv4.add("107.161.21.13");
                DhtNode.ipv4.add("144.76.60.215");
                DhtNode.port.add("33445");
                DhtNode.port.add("33445");
                DhtNode.port.add("33445");
                DhtNode.key.add("FE3914F4616E227F29B2103450D6B55A836AD4BD23F97144E2C4ABE8D504FE1B");
                DhtNode.key.add("5848E6344856921AAF28DAB860C5816780FE0C8873AAC415C1B7FA7FAA4EF046");
                DhtNode.key.add("04119E835DF3E78BACF0F84235B300546AF8B936F035185E2A8E9E0A67C8924F");
                DhtNode.ipv6.add("");
                DhtNode.ipv6.add("");
                DhtNode.ipv6.add("");
                DhtNode.owner.add("");
                DhtNode.owner.add("");
                DhtNode.owner.add("");
                DhtNode.location.add("");
                DhtNode.location.add("");
                DhtNode.location.add("");
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "About to ping servers...");
            /**
             * Ping servers to find quickest connection - Threading this would be goood
             */
            long shortestTime = 99999;
            int pos = -1;
            Socket socket = null;
            Log.d(TAG, "DhtNode size: " + DhtNode.ipv4.size());
            for(int i = 0;i < DhtNode.ipv4.size(); i++) {
                try {
                    long currentTime = System.currentTimeMillis();
                    boolean reachable = InetAddress.getByName(DhtNode.ipv4.get(i)).isReachable(400);
                    long elapsedTime = System.currentTimeMillis() - currentTime;
                    if (reachable && (elapsedTime < shortestTime)) {
                        shortestTime = elapsedTime;
                        pos = i;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    e.printStackTrace();
                }
            }

            if(socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

             /* Move quickest node to front of list */
            if(pos != -1) {
                DhtNode.ipv4.add(0, DhtNode.ipv4.get(pos));
                DhtNode.ipv6.add(0, DhtNode.ipv6.get(pos));
                DhtNode.port.add(0, DhtNode.port.get(pos));
                DhtNode.key.add(0, DhtNode.key.get(pos));
                DhtNode.owner.add(0, DhtNode.owner.get(pos));
                DhtNode.location.add(0, DhtNode.location.get(pos));
                Log.d(TAG, "DHT Nodes have been sorted");
                DhtNode.sorted = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            /**
             * There is a chance that downloading finishes later than the bootstrapping call in the
             * ToxService, because both are in separate threads. In that case to make sure the nodes
             * are bootstrapped we restart the ToxService
             */
            if(!DhtNode.connected)
            {
                Log.d(TAG, "Restarting START_TOX as DhtNode.connected returned false");
                Intent restart = new Intent(getApplicationContext(), ToxDoService.class);
                restart.setAction(Constants.START_TOX);
                getApplicationContext().startService(restart);
            }

            /* Restart intent if it was connected before nodes were sorted */
            if(DhtNode.connected && !DhtNode.sorted) {
                Log.d(TAG, "Restarting START_TOX as DhtNode.sorted was false");
                Intent restart = new Intent(getApplicationContext(), ToxDoService.class);
                restart.setAction(Constants.START_TOX);
                getApplicationContext().startService(restart);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!pane.isOpen()) {
            pane.openPane();
        } else {
            finish();
        }
    }

    private void restartActivity() {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        startActivity(intent);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==Constants.ADD_FRIEND_REQUEST_CODE && resultCode==RESULT_OK){
            updateLeftPane();
        } else if(requestCode==Constants.SENDFILE_PICKEDFRIEND_CODE && resultCode==RESULT_OK) {
            Uri uri=  data.getData();
            File pickedFile = new File(uri.getPath());
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            Log.d("file picked",""+pickedFile.getAbsolutePath() );
            Log.d("file type",""+getContentResolver().getType(uri));
        } else if(requestCode==Constants.UPDATE_SETTINGS_REQUEST_CODE && resultCode==RESULT_OK) {
            restartActivity();
        } else if(requestCode==Constants.WELCOME_ACTIVITY_REQUEST_CODE && resultCode==RESULT_CANCELED) {
            finish();
        }
    }

    private class PaneListener implements SlidingPaneLayout.PanelSlideListener {

        @Override
        public void onPanelClosed(View view) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            setTitle(activeTitle);

            // Hide add friend icon
            MenuItem af = menu.findItem(R.id.add_friend);
            MenuItemCompat.setShowAsAction(af,MenuItem.SHOW_AS_ACTION_NEVER);

            //Hide group menu
            ActionBar bar = MainActivity.this.getSupportActionBar();
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

            // Hide search icon
            MenuItem search = menu.findItem(R.id.search_friend);
            MenuItemCompat.setShowAsAction(search, MenuItem.SHOW_AS_ACTION_NEVER);

            //ag.setVisible(true);
            /* Hide until functionality is implemented to avoid confusion
            MenuItemCompat.setShowAsAction(ag,MenuItem.SHOW_AS_ACTION_ALWAYS);
            ag.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    addFriendToGroup();
                    return false;
                }
            });
            */
            toxSingleton.rightPaneActive = true;
            toxSingleton.leftPaneActive = false;
            if(toxSingleton.activeFriendKey!=null){
                updateChat(toxSingleton.activeFriendKey);
            }
            clearUselessNotifications();
        }

        @Override
        public void onPanelOpened(View view) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);

            // Show add friend icon
            MenuItem af = menu.findItem(R.id.add_friend);
            MenuItemCompat.setShowAsAction(af,MenuItem.SHOW_AS_ACTION_IF_ROOM);

            //Show group dropdown
            ActionBar bar = MainActivity.this.getSupportActionBar();
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            // Show search icon
            MenuItem search = menu.findItem(R.id.search_friend);
            MenuItemCompat.setShowAsAction(search, MenuItem.SHOW_AS_ACTION_ALWAYS);

            //menu.removeGroup(666);

            supportInvalidateOptionsMenu();

            toxSingleton.rightPaneActive =false;
            toxSingleton.leftPaneActive = true;
            InputMethodManager imm = (InputMethodManager)getSystemService(
                    Context.INPUT_METHOD_SERVICE);
        }

        @Override
        public void onPanelSlide(View view, float arg1) {
        }

    }


}
