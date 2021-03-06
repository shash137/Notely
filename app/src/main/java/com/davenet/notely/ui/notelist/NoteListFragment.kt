package com.davenet.notely.ui.notelist

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davenet.notely.LoginActivity
import com.davenet.notely.R
import com.davenet.notely.databinding.FragmentNoteListBinding
import com.davenet.notely.domain.NoteEntry
import com.davenet.notely.ui.NoteListener
import com.davenet.notely.ui.NotesAdapter
import com.davenet.notely.util.UIState
import com.davenet.notely.viewmodels.NoteListViewModel
import com.davenet.notely.viewmodels.NoteListViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.android.synthetic.main.fragment_note_list.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.design.longSnackbar


class NoteListFragment : Fragment() {

    private lateinit var noteListViewModel: NoteListViewModel
    private lateinit var uiScope: CoroutineScope
    private lateinit var binding: FragmentNoteListBinding
    private lateinit var coordinator: CoordinatorLayout
    private lateinit var noteList: LiveData<List<NoteEntry>>
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_note_list, container, false
        )

        auth = FirebaseAuth.getInstance()

        val application = requireNotNull(this.activity).application

        val viewModelFactory = NoteListViewModelFactory(application)

        noteListViewModel =
            ViewModelProvider(this, viewModelFactory).get(NoteListViewModel::class.java)


        val adapter = NotesAdapter(NoteListener {
            noteListViewModel.onNoteClicked(it)
        })

        noteListViewModel.apply {
            notes.observe(viewLifecycleOwner, {
                it?.let {
                    if (it.isNotEmpty()) {
                        setHasOptionsMenu(true)
                        noteListViewModel.uiState.set(UIState.HAS_DATA)
                    } else {
                        setHasOptionsMenu(false)
                        noteListViewModel.uiState.set(UIState.EMPTY)
                    }
                    adapter.submitToList(it)
                }
            })

            navigateToNoteDetail.observe(viewLifecycleOwner, { note ->
                note?.let {
                    val bundle = Bundle()
                    bundle.putParcelable("note", note)
                    findNavController().navigate(
                        R.id.action_noteListFragment_to_editNoteFragment, bundle
                    )
                    noteListViewModel.onNoteDetailNavigated()
                }
            })
        }

        binding.apply {
            lifecycleOwner = this@NoteListFragment
            uiState = noteListViewModel.uiState
            noteList.adapter = adapter
            noteList.layoutManager = LinearLayoutManager(context)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_noteListFragment_to_editNoteFragment)
        }

        uiScope = CoroutineScope(Dispatchers.Default)

        noteList = noteListViewModel.notes
        coordinator = activity?.findViewById(R.id.list_coordinator)!!
        ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }


            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val noteToErase = noteList.value?.get(position)
                deleteNote(noteToErase!!)
                coordinator.longSnackbar("Note deleted", "Undo") {
                    insertNote(noteToErase)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {

                RecyclerViewSwipeDecorator.Builder(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addActionIcon(R.drawable.ic_baseline_delete_24)
                    .setActionIconTint(Color.WHITE)
                    .addBackgroundColor(Color.RED)
                    .create()
                    .decorate()

                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }).attachToRecyclerView(binding.noteList)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null){
            findNavController().navigate(R.id.action_noteListFragment_to_loginActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                Log.d("delete", "Delete selected")
                deleteAllNotes()
                undoDeleteNotes(noteList.value!!)
                true
            }
            R.id.action_settings -> {
                findNavController().navigate(R.id.action_noteListFragment_to_settingsActivity2)
                true
            }
            R.id.action_logout -> {
                FirebaseAuth.getInstance().signOut()
                findNavController().navigate(R.id.action_noteListFragment_to_loginActivity)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun undoDeleteNotes(noteList: List<NoteEntry>) {
        coordinator.longSnackbar("Notes deleted", "Undo") {
            insertAllNotes(noteList)
        }
    }

    private fun insertAllNotes(noteList: List<NoteEntry>) {
        uiScope.launch {
            withContext(Dispatchers.Main) {
                noteListViewModel.insertAllNotes(noteList)
            }
        }
    }

    private fun deleteAllNotes() {
        uiScope.launch {
            withContext(Dispatchers.Main) {
                noteListViewModel.deleteAllNotes()
            }
        }
    }

    private fun deleteNote(note: NoteEntry) {
        uiScope.launch {
            withContext(Dispatchers.Main) {
                noteListViewModel.deleteNote(note)
            }
        }
    }

    private fun insertNote(note: NoteEntry) {
        uiScope.launch {
            withContext(Dispatchers.Main) {
                noteListViewModel.insertNote(note)
            }
        }
    }
}