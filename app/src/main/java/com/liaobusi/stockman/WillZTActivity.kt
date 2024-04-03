package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman.databinding.FragmentFollowListBinding
import com.liaobusi.stockman.databinding.FragmentWillZtBinding
import com.liaobusi.stockman.databinding.ItemFollowBinding
import com.liaobusi.stockman.databinding.ItemWillZtBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WillZTActivity : AppCompatActivity() {

    companion object {
        fun startWillZTActivity(context: Context) {
            val i = Intent(context, WillZTActivity::class.java)
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_will_zt)
        supportActionBar?.title = "冲刺涨停"
    }
}


class WillZTFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = FragmentWillZtBinding.inflate(inflater, container, false)

        binding.rv.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            binding.rv.adapter =
                WillZTAdapter()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                StockRepo.getRealTimeStocks()
                val willZtStocks = Injector.appDatabase.stockDao().getWillZTStocks()
                launch(Dispatchers.Main) {
                    (binding.rv.adapter as WillZTAdapter).submitList((willZtStocks) as MutableList<Stock>)
                }
                delay(2000)
            }
        }

        return binding.root
    }

    inner class DiffCallback : DiffUtil.ItemCallback<Stock>() {
        override fun areItemsTheSame(oldItem: Stock, newItem: Stock): Boolean {
            return oldItem.code == newItem.code
        }

        override fun areContentsTheSame(oldItem: Stock, newItem: Stock): Boolean {
            return oldItem.chg == newItem.chg && oldItem.price == newItem.price
        }

    }

    inner class WillZTAdapter : ListAdapter<Stock, WillZTAdapter.VH>(DiffCallback()) {


        inner class VH(val binding: ItemWillZtBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Stock, position: Int) {
                binding.chgTv.setTextColor(Color.BLACK)
                if (item is Stock) {
                    binding.name.text = item.name
                    if (item.chg > 0) {
                        binding.chgTv.setTextColor(Color.RED)
                    } else if (item.chg < 0) {
                        binding.chgTv.setTextColor(Color.parseColor("#ff00ad43"))
                    }
                    binding.chgTv.text = item.chg.toString() + "%"
                    binding.root.setOnClickListener {
                        item.openWeb(it.context)
                    }
                }


            }

        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemWillZtBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }


        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), position)
        }

    }


}