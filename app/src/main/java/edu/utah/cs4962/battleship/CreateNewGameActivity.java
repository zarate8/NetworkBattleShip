package edu.utah.cs4962.battleship;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Jesus Zarate on 11/15/14.
 */
public class CreateNewGameActivity extends Activity
{
    private EditText gameNameEditText;
    private EditText playerNameEditText;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout GameNameLayout = new LinearLayout(this);
        LinearLayout PlayerNameLayout = new LinearLayout(this);
        GameNameLayout.setOrientation(LinearLayout.HORIZONTAL);
        PlayerNameLayout.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams params1 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.gravity = Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL;
        params1.gravity = Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL;

        TextView GameNameTextView = new TextView(this);
        GameNameTextView.setText("Game Name");
        GameNameTextView.setTextSize(20);
        gameNameEditText = new EditText(this);

        TextView PlayerName = new TextView(this);
        PlayerName.setText("Player's Name");
        PlayerName.setTextSize(20);
        playerNameEditText = new EditText(this);


        Button submitButton = new Button(this);
        submitButton.setText("Submit");
        submitButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                // TODO: Return the player's name and the game name on the intent results
                setIntentResults();
                finish();
            }
        });


        GameNameLayout.addView(GameNameTextView, params1);
        GameNameLayout.addView(gameNameEditText, params1);
        PlayerNameLayout.addView(PlayerName, params1);
        PlayerNameLayout.addView(playerNameEditText, params1);

        rootLayout.addView(GameNameLayout, params);
        rootLayout.addView(PlayerNameLayout, params);
        rootLayout.addView(submitButton, params);
        setContentView(rootLayout);
    }

    public void setIntentResults()
    {
        // Include the color the use picked so that it can also be updated in the
        // button preview of the Create Mode.
        Intent resultIntent = new Intent();
        resultIntent.putExtra(BattleShipActivity.GAME_NAME, gameNameEditText.getText().toString());
        resultIntent.putExtra(BattleShipActivity.PLAYER_NAME, playerNameEditText.getText().toString());

//        String gameName = gameNameEditText.getText().toString();
//        String[] editTextboxResults = {gameNameEditText.getText().toString(), playerNameEditText.getText().toString()};


        setResult(Activity.RESULT_OK, resultIntent);
    }
}
