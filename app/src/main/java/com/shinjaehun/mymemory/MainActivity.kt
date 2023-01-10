package com.shinjaehun.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.shinjaehun.mymemory.models.BoardSize
import com.shinjaehun.mymemory.models.MemoryGame
import com.shinjaehun.mymemory.models.UserImageList
import com.shinjaehun.mymemory.utils.EXTRA_BOARD_SIZE
import com.shinjaehun.mymemory.utils.EXTRA_GAME_NAME
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248 //??
    }

    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var clRoot: CoordinatorLayout

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        clRoot = findViewById(R.id.clRoot)

//        // create activity 테스트 코드
//        val intent = Intent(this, CreateActivity::class.java)
//        intent.putExtra(EXTRA_BOARD_SIZE, BoardSize.EASY)
//        startActivity(intent)

        setupBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.mi_refresh -> {
                if(memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        setupBoard()
                    })
                } else {
                    // setup the game again
                    setupBoard()
                }
                return true
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // CreateActivity의 handleAllImagesUploaded()에서 finish()에 의해 MainActivity로 돌아왔을 때 재호출
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
           val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
           if (customGameName == null) {
               Log.e(TAG, "Got null custom game from Create Activity")
               return
           }
           downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // download 메뉴에서 선택!
    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            // grab the text of the game name that the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload) // 이걸 이렇게 해서 연결!!!
        })
    }

    // 게임 이름으로 firestore에 조회해서 이미지 경로 받아오기
    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalild custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry, we couldn't find any such game, '$customGameName'", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            // prefatch images : 이미지를 미리 다운로드해서 cache에 저장해두면 훨씬 빨라지겠지
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're now playing '$customGameName'!", Snackbar.LENGTH_LONG).show()

            gameName = customGameName
            setupBoard() // 기존 setupBoard()도 변경 불가피
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create your own memory board", boardSizeView, View.OnClickListener {
            // set a new value for the board size
            val desiredBoardSize = when(radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // navigate to a new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }

        showAlertDialog("Choose new size", boardSizeView, View.OnClickListener {
            // set a new value for the board size
            boardSize = when(radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // 사이즈 변경일 때 custom game의 data를 그대로 사용하지 않기 위함?
            // custom game은 easy로 파일 4을 받아 왔는데
            // game size를 변경하면 파일 수가 안 맞잖아!
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> // kotlin specific 한 부분인데 공부 필요!
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name) // 커스텀 게임인 경우 action bar에서 게임 이름 바꿔주기

        when(boardSize){
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM ->  {
                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD ->  {
                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }

        // 여기서 초기값을 줘야 color_progress_none부터 interpolation이 시작됨!
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))

        memoryGame = MemoryGame(boardSize, customGameImages) // customGameImages가 존재하는지에 따라 게임 로직이 달라짐
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position: Int) {
//                Log.i(TAG, "Card clicked $position")
                updateGameWithFlip(position)
            }
        })

        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        // error checking
        if (memoryGame.haveWonGame()) {
            Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }

        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()
            return
        }

        // actually flip over the card
        if(memoryGame.flipCard(position)){
            Log.i(TAG, "Found a match! Num paris found: ${memoryGame.numPairsFound}")
            // android에서 color interpolation : 카드 뒤집을때마다 tvNumPairs 글자 색깔 바꾸기
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full),
            ) as Int

            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()) { // show the winning message
                Snackbar.make(clRoot, "You won! Congratulations!", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }

        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"

        adapter.notifyDataSetChanged()
    }
}