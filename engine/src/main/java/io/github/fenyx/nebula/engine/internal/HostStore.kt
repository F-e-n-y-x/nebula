package io.github.fenyx.nebula.engine.internal

import android.content.Context
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails

/** Persistence for saved hosts; the real one shares V+'s computer database and pair-name map. */
interface HostStore {
    fun all(): List<ComputerDetails>
    fun save(details: ComputerDetails)
    fun delete(details: ComputerDetails)
    fun pairName(uuid: String): String
    fun savePairName(uuid: String, pairName: String)
}

class DatabaseHostStore(context: Context) : HostStore {
    private val appContext = context.applicationContext
    private val pairNames = appContext.getSharedPreferences(PAIR_NAME_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    private inline fun <T> withDb(block: (ComputerDatabaseManager) -> T): T = synchronized(lock) {
        val db = ComputerDatabaseManager(appContext)
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    override fun all(): List<ComputerDetails> = withDb { it.getAllComputers() }

    override fun save(details: ComputerDetails) {
        withDb { it.updateComputer(details) }
    }

    override fun delete(details: ComputerDetails) {
        withDb { it.deleteComputer(details) }
    }

    override fun pairName(uuid: String): String = pairNames.getString(uuid, "") ?: ""

    override fun savePairName(uuid: String, pairName: String) {
        pairNames.edit().putString(uuid, pairName).apply()
    }

    private companion object {
        const val PAIR_NAME_PREFS = "pair_name_map"
    }
}
