package edu.utah.cs4962.battleship;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by Jesus Zarate on 11/19/14.
 *
 * UID - U0816141
 * Network Battleship. (READY TO GO!)
 * Class - CS 4962 Mobile Application Programming: Android
 */
public class BattleShipActivity extends Activity
{
    public final static String PLAYERS_TURN = "players_turn";
    public final static String GAME_NAME = "game_name";
    public final static String PLAYER_NAME = "player_name";

    public final static int JOIN_GAME_REQUEST_CODE = 13;

    public FragmentManager fragmentManager;
    public GameFragment _gameFragment;
    public GameListFragment _gameListFragment;
    public FragmentTransaction _addTransaction;
    public NetworkClass _networkClass;

    public static boolean _waitingToJoinGame = false;

    LinearLayout secondLayout = null;
    LinearLayout gameListLayout = null;
    FrameLayout gameLayout = null;

    LinearLayout.LayoutParams params = null;
    boolean YOUR_TURN = true;
    TextView playerTurnText;

    Gson _gson = new Gson();

    Timer timer = new Timer();

    public static int LaunchedMissileAtPosition = 0;

    // Key -> gameId, Value -> playerId
    HashMap<String, String> MyNetworkGames = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
            savedInstanceState = null;

        super.onCreate(savedInstanceState);

        if (isTabletDevice(getResources()))
        {
            TabletMode();
        } else
        {
            PhoneMode();
        }
    }

    private void PhoneMode()
    {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout firstLayout = new LinearLayout(this);
        firstLayout.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        firstLayout.addView(buttonLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        _gameFragment = new GameFragment();
        _gameListFragment = new GameListFragment();

        // Second Layout
        secondLayout = new LinearLayout(this);
        secondLayout.setOrientation(LinearLayout.HORIZONTAL);

        gameListLayout = new LinearLayout(this);
        gameListLayout.setId(11);
        gameListLayout.setBackgroundColor(Color.CYAN);

        gameLayout = new FrameLayout(this);
        gameLayout.setId(10);

        _networkClass = new NetworkClass(BattleShipActivity.this);
        _networkClass.getGameList(this); // Get game list from network.


        //region <Network Listeners>

        _networkClass.setOnGameListArrivedListener(new NetworkClass.OnGameListArrivedListener()
       {
           @Override
           public void OnGameListArrived(NetworkClass networkClass, NetworkClass.Game[] _games)
           {
               Arrays.sort(_games, new Comparator<NetworkClass.Game>()
               {
                   @Override
                   public int compare(NetworkClass.Game game, NetworkClass.Game game2)
                   {
                       return game.name.compareTo(game2.name);
                   }
               });

               _gameListFragment.setGameList(_games);

               // Initialize last selected game on the list
               if (_gameListFragment.selectedGame <= _gameListFragment.getCount())
               {
                   String gameId = _gameListFragment.getSelectedGameId();
                   if(MyNetworkGames.containsKey(gameId))
                   {
                       String playerId = MyNetworkGames.get(gameId);
                       _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
                       timer.schedule(new CheckForPlayerTurnTask(), 3000);
                   }
               }
           }
       }

        );

        _networkClass.setOnBattleGridUpdatedListener(new NetworkClass.OnBattleGridUpdatedListener()
        {
            @Override
            public void OnBattleGridUpdated (NetworkClass networkClass, HashMap < String, NetworkClass.Cell[]>battleGrid)
            {
                if (battleGrid != null)
                {
                    _gameFragment.setGame(battleGrid);
                } else
                {
                    Toast.makeText(BattleShipActivity.this, "You cannot play this game", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // Meaning a new game was able to be created successfully.
        _networkClass.setOnNewGameInfoArrivedListener(new NetworkClass.OnNewGameInfoArrivedListener()
          {
              @Override
              public void OnGameInfoArrived (NetworkClass networkClass, HashMap < String, String > gameInfo)
              {
                  if (gameInfo != null)
                  {
                      if (gameInfo.containsKey(NetworkClass.GAME_ID) && gameInfo.containsKey(NetworkClass.PLAYER_ID))
                      {
                          String gameId = gameInfo.get(NetworkClass.GAME_ID);
                          String playerId = gameInfo.get(NetworkClass.PLAYER_ID);
                          MyNetworkGames.put(gameId, playerId);

                          // Save this information immediately to the file to prevent data loss.
                          saveGames();

                          _networkClass.getGameList(BattleShipActivity.this);
                      }
                  } else
                  {
                      Toast.makeText(BattleShipActivity.this, "Unable to create new game.", Toast.LENGTH_SHORT).show();
                  }
              }
          }

        );

        _networkClass.setOnErrorReceivedListener(new NetworkClass.OnErrorReceivedListener()
         {
             @Override
             public void OnErrorReceived (NetworkClass networkClass, String errorMessage)
             {
                 Toast.makeText(BattleShipActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
             }
         }
        );

        _networkClass.setOnJoinRequestReceivedListener(new NetworkClass.OnJoinRequestReceivedListener()
        {
            @Override
            public void OnJoinRequestReceived (NetworkClass networkClass)
            {
                _waitingToJoinGame = true;

                // Start the new activity that allows the user to enter his player name
                // and allows him to join the game.
                startJoinGameActivity(BattleShipActivity.this);
            }
        });


        _networkClass.setOnPlayerIdReceivedListener(new NetworkClass.OnPlayerIdReceivedListener()
        {
            @Override
            public void OnPlayerIdReceived (NetworkClass networkClass, String playerId)
            {
                // No longer waiting to join game.
                _waitingToJoinGame = false;

                String selectedGame = _gameListFragment.getSelectedGameId();
                // Store the game
                MyNetworkGames.put(selectedGame, playerId);

                // Save the game information immediately to avoid data loss.
                saveGames();

                // Request the game.
                // Will only retrieve if the game
                // corresponds to this player and if the game is in progress.
                _networkClass.initBattleGrid(BattleShipActivity.this, selectedGame, playerId);

                timer.schedule(new CheckForPlayerTurnTask(), 3000);
            }
        }

        );

        _networkClass.setOnNeedToUpdateBattleGridListener(new NetworkClass.OnNeedToUpdateBattleGridListener()
        {
            @Override
            public void OnNeedToUpdateBattleGrid (NetworkClass networkClass, String playersTurn,
                                                  boolean myTurn)
            {
                setTitle(playersTurn);

                if (myTurn)
                {
                    String gameId = _gameListFragment.getSelectedGameId();
                    String playerId = MyNetworkGames.get(gameId);
                    _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
                }

                timer.schedule(new CheckForPlayerTurnTask(), 3000);
            }
        });

        _networkClass.setOnMissileLaunchResultArrivedListener(new NetworkClass.OnMissileLaunchResultArrivedListener()
        {
            @Override
            public void OnMissileLaunchResultArrived (NetworkClass networkClass, String result)
            {
                int xPos = LaunchedMissileAtPosition / BattleGridView.GridSize;
                int yPos = LaunchedMissileAtPosition % BattleGridView.GridSize;

                _gameFragment.recordAttack(xPos, yPos, result);

                String gameId = _gameListFragment.getSelectedGameId();
                String playerId = MyNetworkGames.get(gameId);
                _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
            }
        });

        _networkClass.setOnGameEndedListener(new NetworkClass.OnGameEndedListener()
        {
            @Override
            public void OnGameEnded(NetworkClass networkClass, String winner)
            {
                setTitle("WINNER: " + winner);
                Toast.makeText(BattleShipActivity.this, winner + " Won", Toast.LENGTH_SHORT).show();
                timer.cancel();
            }
        });
        //endregion <Network Listeners>

        //region NewGameButton

        Button newGameButton = new Button(this);
        newGameButton.setText("New Game");
        buttonLayout.addView(newGameButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        newGameButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                //Start the new game activity
                startNewGameActivity(BattleShipActivity.this);
            }
        });
         //endregion NewGameButton


        LinearLayout.LayoutParams backButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backButtonParams.gravity = Gravity.RIGHT;
        Button backButton = new Button(this);
        backButton.setText("Back");
        buttonLayout.addView(backButton, backButtonParams);

        backButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                setProperWindowSize(true);

                showWindow("List");

                setTitle("Battle Ship");

                onPause();
            }
        });

        //region <Refresh Button>
        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        buttonLayout.addView(refreshButton,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshButton.setOnClickListener(new View.OnClickListener()
         {
             @Override
             public void onClick (View view)
             {
                 _networkClass.getGameList(BattleShipActivity.this); // Get game list from network.
             }
         });

        //endregion <Refresh Button>

        _gameListFragment.setOnGameSelectedListener(new GameListFragment.OnGameSelectedListener()
        {
            @Override
            public void onGameSelected(GameListFragment gameListFragment, NetworkClass.Game g)
            {
                setProperWindowSize(false);

                if(timer == null)
                {
                    timer = new Timer();
                }
                if (MyNetworkGames.containsKey(g.id) && g.status.equals(GameFragment.GAME_STATUS_WAITING))
                {
                    setTitle("Waiting for player to join");

                } else if (!_waitingToJoinGame)
                {
                    // Get the player id to retrieve the battle grid.
                    String playerId = MyNetworkGames.get(g.id);

                    if (playerId != null && g.status.equals(GameFragment.GAME_STATUS_PLAYING))//playerId != null)
                    {
                        // Request the game.
                        // Will only retrieve if the game
                        // corresponds to this player and if the game is in progress.
                        _networkClass.initBattleGrid(BattleShipActivity.this, g.id, playerId);
                        timer = new Timer();
                        timer.schedule(new CheckForPlayerTurnTask(), 1000);

                    } else if (g.status.equals(GameFragment.GAME_STATUS_WAITING))
                    {
                        startJoinGameActivity(BattleShipActivity.this);
                    } else
                    {
                        //_gameFragment.clearBattleGrid();
                        Toast.makeText(BattleShipActivity.this, "You are not part of this game.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        _gameFragment.setOnUpdateGameListListener(new GameFragment.OnUpdateGameListListener()
        {
            @Override
            public void OnUpdateGameList(GameFragment gameFragment)
            {
                _gameListFragment.updateList();
            }
        });

        _gameFragment.setOnMissileLaunchListener(new GameFragment.OnLaunchMissileListener()
         {
             @Override
             public void OnLauchMissile (GameFragment gameFragment,int xPos, int yPos)
             {
                 String gameId = _gameListFragment.getSelectedGameId();
                 String playerId = MyNetworkGames.get(gameId);
                 _networkClass.LaunchMissile(BattleShipActivity.this, gameId, playerId, xPos, yPos);

                 if(YOUR_TURN)
                 {
                     _gameFragment.recordAttack(xPos, yPos, "Launch");
                 }
                 // Request status of the game to the server every 5 seconds to check if it is your
                 // turn.
                 //timer.schedule(new CheckForPlayerTurnTask(), 1000);
             }
         }
        );

        secondLayout.addView(gameListLayout, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 100
        ));

        params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0);

        secondLayout.addView(gameLayout, params);

        fragmentManager = getFragmentManager();
        _addTransaction = fragmentManager.beginTransaction();

        _addTransaction.add(10, _gameFragment);

        _addTransaction.add(11, _gameListFragment);

        _addTransaction.commit();

        rootLayout.addView(firstLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        rootLayout.addView(secondLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 90
        ));
        setContentView(rootLayout);
    }

    private void TabletMode()
    {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout firstLayout = new LinearLayout(this);
        firstLayout.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        firstLayout.addView(buttonLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        _gameFragment = new GameFragment();
        _gameListFragment = new GameListFragment();

        // Second Layout
        secondLayout = new LinearLayout(this);
        secondLayout.setOrientation(LinearLayout.HORIZONTAL);

        gameListLayout = new LinearLayout(this);
        gameListLayout.setId(11);
        gameListLayout.setBackgroundColor(Color.CYAN);

        gameLayout = new FrameLayout(this);
        gameLayout.setId(10);

        _networkClass = new NetworkClass(BattleShipActivity.this);
        _networkClass.getGameList(this); // Get game list from network.


        //region <Network Listeners>

        _networkClass.setOnGameListArrivedListener(new NetworkClass.OnGameListArrivedListener()
        {
            @Override
            public void OnGameListArrived(NetworkClass networkClass, NetworkClass.Game[] _games)
            {
                Arrays.sort(_games, new Comparator<NetworkClass.Game>()
                {
                    @Override
                    public int compare(NetworkClass.Game game, NetworkClass.Game game2)
                    {
                        return game.name.compareTo(game2.name);
                    }
                });

                _gameListFragment.setGameList(_games);

                // Initialize last selected game on the list
                if (_gameListFragment.selectedGame <= _gameListFragment.getCount())
                {
                    String gameId = _gameListFragment.getSelectedGameId();
                    if(MyNetworkGames.containsKey(gameId))
                    {
                        String playerId = MyNetworkGames.get(gameId);
                        _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
                        timer.schedule(new CheckForPlayerTurnTask(), 3000);

                    }
                }
        }
    }

    );

    _networkClass.setOnBattleGridUpdatedListener(new NetworkClass.OnBattleGridUpdatedListener()
    {
        @Override
        public void OnBattleGridUpdated (NetworkClass
        networkClass, HashMap < String, NetworkClass.Cell[]>battleGrid)
        {
            if (battleGrid != null)
            {
                _gameFragment.setGame(battleGrid);
            } else
            {
                Toast.makeText(BattleShipActivity.this, "You cannot play this game", Toast.LENGTH_SHORT).show();
            }
        }
    });


    // Meaning a new game was able to be created successfully.
    _networkClass.setOnNewGameInfoArrivedListener(new NetworkClass.OnNewGameInfoArrivedListener()

    {
        @Override
        public void OnGameInfoArrived (NetworkClass
        networkClass, HashMap < String, String > gameInfo)
        {
            if (gameInfo != null)
            {
                if (gameInfo.containsKey(NetworkClass.GAME_ID) && gameInfo.containsKey(NetworkClass.PLAYER_ID))
                {
                    String gameId = gameInfo.get(NetworkClass.GAME_ID);
                    String playerId = gameInfo.get(NetworkClass.PLAYER_ID);
                    MyNetworkGames.put(gameId, playerId);

                    // Save this information immediately to the file to prevent data loss.
                    saveGames();

                    _networkClass.getGameList(BattleShipActivity.this);
                }
            } else
            {
                Toast.makeText(BattleShipActivity.this, "Unable to create new game.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    );

    _networkClass.setOnErrorReceivedListener(new NetworkClass.OnErrorReceivedListener()

    {
        @Override
        public void OnErrorReceived (NetworkClass networkClass, String errorMessage)
        {
            Toast.makeText(BattleShipActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }
    );

    _networkClass.setOnJoinRequestReceivedListener(new NetworkClass.OnJoinRequestReceivedListener()
    {
        @Override
        public void OnJoinRequestReceived (NetworkClass networkClass)
        {
            _waitingToJoinGame = true;

            // Start the new activity that allows the user to enter his player name
            // and allows him to join the game.
            startJoinGameActivity(BattleShipActivity.this);
        }
    });


    _networkClass.setOnPlayerIdReceivedListener(new NetworkClass.OnPlayerIdReceivedListener()
    {
        @Override
        public void OnPlayerIdReceived (NetworkClass networkClass, String playerId)
        {
            // No longer waiting to join game.
            _waitingToJoinGame = false;

            String selectedGame = _gameListFragment.getSelectedGameId();
            // Store the game
            MyNetworkGames.put(selectedGame, playerId);

            // Save the game information immediately to avoid data loss.
            saveGames();

            // Request the game.
            // Will only retrieve if the game
            // corresponds to this player and if the game is in progress.
            _networkClass.initBattleGrid(BattleShipActivity.this, selectedGame, playerId);

            timer.schedule(new CheckForPlayerTurnTask(), 3000);
        }
    });

    _networkClass.setOnNeedToUpdateBattleGridListener(new NetworkClass.OnNeedToUpdateBattleGridListener()
    {
        @Override
        public void OnNeedToUpdateBattleGrid (NetworkClass networkClass, String playersTurn,
        boolean myTurn)
        {
            setTitle(playersTurn);

            if (myTurn)
            {
                String gameId = _gameListFragment.getSelectedGameId();
                String playerId = MyNetworkGames.get(gameId);
                _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
            }

            timer.schedule(new CheckForPlayerTurnTask(), 3000);
        }
    });

    _networkClass.setOnMissileLaunchResultArrivedListener(new NetworkClass.OnMissileLaunchResultArrivedListener()
    {
        @Override
        public void OnMissileLaunchResultArrived (NetworkClass networkClass, String result)
        {
            int xPos = LaunchedMissileAtPosition / BattleGridView.GridSize;
            int yPos = LaunchedMissileAtPosition % BattleGridView.GridSize;

            _gameFragment.recordAttack(xPos, yPos, result);

            String gameId = _gameListFragment.getSelectedGameId();
            String playerId = MyNetworkGames.get(gameId);
            _networkClass.initBattleGrid(BattleShipActivity.this, gameId, playerId);
        }
    });

    _networkClass.setOnGameEndedListener(new NetworkClass.OnGameEndedListener()
    {
        @Override
        public void OnGameEnded(NetworkClass networkClass, String winner)
        {
            setTitle("WINNER: " + winner);
            Toast.makeText(BattleShipActivity.this, winner + " Won", Toast.LENGTH_SHORT).show();
            timer.cancel();
        }
    });
    //endregion <Network Listeners>


    Button newGameButton = new Button(this);
    newGameButton.setText("New Game");
    buttonLayout.addView(newGameButton,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

    newGameButton.setOnClickListener(new View.OnClickListener()

    {
        @Override
        public void onClick (View view)
        {
            //Start the new game activity
            startNewGameActivity(BattleShipActivity.this);
        }
    }

    );

    Button refreshButton = new Button(this);
    refreshButton.setText("Refresh");
    buttonLayout.addView(refreshButton,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    refreshButton.setOnClickListener(new View.OnClickListener()

    {
        @Override
        public void onClick (View view)
        {
            _networkClass.getGameList(BattleShipActivity.this); // Get game list from network.
        }
    }

    );
    //endregion NewGameButton


    //region Select Game
    _gameListFragment.setOnGameSelectedListener(new GameListFragment.OnGameSelectedListener()

    {
        @Override
        public void onGameSelected (GameListFragment gameListFragment, NetworkClass.Game g)
        {
            if(timer == null)
            {
                timer = new Timer();
            }
            if (MyNetworkGames.containsKey(g.id) && g.status.equals(GameFragment.GAME_STATUS_WAITING))
            {
                setTitle("Waiting for player to join");

            } else if (!_waitingToJoinGame)
            {
                // Get the player id to retrieve the battle grid.
                String playerId = MyNetworkGames.get(g.id);

                if (playerId != null && g.status.equals(GameFragment.GAME_STATUS_PLAYING))//playerId != null)
                {
                    // Request the game.
                    // Will only retrieve if the game
                    // corresponds to this player and if the game is in progress.
                    _networkClass.initBattleGrid(BattleShipActivity.this, g.id, playerId);
                    timer = new Timer();
                    timer.schedule(new CheckForPlayerTurnTask(), 1000);

                } else if (g.status.equals(GameFragment.GAME_STATUS_WAITING))
                {
                    startJoinGameActivity(BattleShipActivity.this);
                } else
                {
                    //_gameFragment.clearBattleGrid();
                    Toast.makeText(BattleShipActivity.this, "You are not part of this game.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    );
    //endregion Select Game

    _gameFragment.setOnUpdateGameListListener(new GameFragment.OnUpdateGameListListener()

    {
        @Override
        public void OnUpdateGameList (GameFragment gameFragment)
        {
            _gameListFragment.updateList();
        }
    }

    );

    _gameFragment.setOnMissileLaunchListener(new GameFragment.OnLaunchMissileListener()

    {
        @Override
        public void OnLauchMissile (GameFragment gameFragment,int xPos, int yPos)
        {
            String gameId = _gameListFragment.getSelectedGameId();
            String playerId = MyNetworkGames.get(gameId);
            _networkClass.LaunchMissile(BattleShipActivity.this, gameId, playerId, xPos, yPos);

            if(YOUR_TURN)
            {
                _gameFragment.recordAttack(xPos, yPos, "Launch");
            }
            // Request status of the game to the server every 5 seconds to check if it is your
            // turn.
            //timer.schedule(new CheckForPlayerTurnTask(), 1000);
        }
    }

    );

    //region <LayoutSetup>
    secondLayout.addView(gameListLayout,new LinearLayout.LayoutParams(
        0,ViewGroup.LayoutParams.MATCH_PARENT,20
        ));

    params=new LinearLayout.LayoutParams(
        0,ViewGroup.LayoutParams.MATCH_PARENT,80);

    secondLayout.addView(gameLayout,params);


    fragmentManager=

    getFragmentManager();

    _addTransaction=fragmentManager.beginTransaction();

    _addTransaction.add(10,_gameFragment);

    _addTransaction.add(11,_gameListFragment);

    _addTransaction.commit();

    rootLayout.addView(firstLayout,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT
    ));

    rootLayout.addView(secondLayout,new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,0,90
        ));
    //endregion <LayoutSetup>

    setContentView(rootLayout);
}


    private void showWindow(String whatWindow)
    {
        if (whatWindow.equals("List"))
        {
            gameListLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 100));

            gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 0));

        } else if (whatWindow.equals("Fragment"))
        {
            gameListLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 0));

            gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 100));
        }
    }

    public void setProperWindowSize(boolean isListView)
    {
        if (!isListView)
        {
            gameListLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 0));

            gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 100));

        } else
        {
            gameListLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 20));

            gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 80));
        }

    }

    private boolean isTabletDevice(Resources resources)
    {
        int screenLayout = resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        boolean isScreenLarge = (screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE);
        boolean isScreenXlarge = (screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE);
        return (isScreenLarge || isScreenXlarge);
    }

    private void startNewGameActivity(Context context)
    {
        Intent intent = new Intent(context, CreateNewGameActivity.class);

        startActivityForResult(intent, 6);
    }

    private void startJoinGameActivity(Context context)
    {
        Intent intent = new Intent(context, JoinGameActivity.class);

        startActivityForResult(intent, JOIN_GAME_REQUEST_CODE);
    }

class CheckForPlayerTurnTask extends TimerTask
{
    public void run()
    {
        String gameId = _gameListFragment.getSelectedGameId();
        String playerId = MyNetworkGames.get(gameId);

        // Send a request to the user to check who's turn it is.
        _networkClass.CheckGameStatus(BattleShipActivity.this, gameId, playerId);
    }

}

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK)
        {
            // If the request code is equal to join game code
            if (requestCode == JOIN_GAME_REQUEST_CODE)
            {
                String gameId = _gameListFragment.getSelectedGameId();
                String playerName = data.getStringExtra(BattleShipActivity.PLAYER_NAME);

                // Request to join the selected game.
                _networkClass.JoinGame(BattleShipActivity.this, gameId, playerName);

            } else
            {
                String gameName = data.getStringExtra(BattleShipActivity.GAME_NAME);
                String playerName = data.getStringExtra(BattleShipActivity.PLAYER_NAME);

                // Request to create a new game with the given game name and the player name.
                _networkClass.requestNewGame(BattleShipActivity.this, gameName, playerName);
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        timer = new Timer();
        File filesDir = getFilesDir();
        try
        {
            // Retrieve the selected game.
            File file = new File(filesDir, "selectedGame.txt");
            FileReader textReader = new FileReader(file);

            BufferedReader bufferedReader = new BufferedReader(textReader);

            try
            {
                _gameListFragment.selectedGame = Integer.parseInt(bufferedReader.readLine());
            } catch (Exception e)
            {
                String ex = e.toString();
            }
            bufferedReader.close();

            // Retrieve the users network games.
            file = new File(filesDir, "myNetworkGames.txt");
            textReader = new FileReader(file);

            bufferedReader = new BufferedReader(textReader);
            String jsonNetworkGames = bufferedReader.readLine();

            Type networkGamesType = new TypeToken<HashMap<String, String>>()
            {
            }.getType();
            HashMap<String, String> networkGames = _gson.fromJson(jsonNetworkGames, networkGamesType);

            MyNetworkGames = networkGames;

            bufferedReader.close();

        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void saveGames()
    {
        File filesDir = getFilesDir();

        try
        {
            // Save the users network games.
            File file = new File(filesDir, "myNetworkGames.txt");
            FileWriter textWriter = null;
            textWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(textWriter);

            String jsonNetWorkGames = _gson.toJson(MyNetworkGames);
            bufferedWriter.write(jsonNetWorkGames);
            bufferedWriter.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }


    }

    @Override
    protected void onPause()
    {
        super.onPause();

        File filesDir = getFilesDir();
        String jsonGameList = _gson.toJson(GameCollection.getInstance().getGamelist());

        // Uncomment this line of code to be able to set a fresh version of the app.
        //String jsonGameList = _gson.toJson(new ArrayList<Game>());
        try
        {
            // Save the game list.
            File file = new File(filesDir, "gameList.txt");
            FileWriter textWriter = null;
            textWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(textWriter);

            bufferedWriter.write(jsonGameList);
            bufferedWriter.close();

            // Save the selected game
            file = new File(filesDir, "selectedGame.txt");
            textWriter = new FileWriter(file);
            bufferedWriter = new BufferedWriter(textWriter);

            bufferedWriter.write(_gameListFragment.selectedGame + "");
            bufferedWriter.close();


            // Save the users network games.
            file = new File(filesDir, "myNetworkGames.txt");
            textWriter = new FileWriter(file);
            bufferedWriter = new BufferedWriter(textWriter);

//            // Uncomment this to populate the network game list.
//            MyNetworkGames = new HashMap<String, String>();
//            MyNetworkGames.put("91b428bd-9fe4-487c-8aba-946040a6392c", "b891ff56-ef53-4e50-8b11-f0070bbf4f02");
//            MyNetworkGames.put("62f4671f-386c-4a31-b381-a69e3142e28f", "4f30d0cc-2c3a-4ab4-9b2d-bd4cc77a9f52");

            String jsonNetWorkGames = _gson.toJson(MyNetworkGames);
            bufferedWriter.write(jsonNetWorkGames);
            bufferedWriter.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        timer.cancel();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

}
