package com.liaobusi.stockman

import android.graphics.Color
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
import com.liaobusi.stockman.databinding.ItemFollowBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FollowListFragment : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentFollowListBinding.inflate(inflater, container, false)

        binding.rv.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            binding.rv.adapter =
                FollowAdapter()
        }


        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                StockRepo.getRealTimeStocks()
                StockRepo.getRealTimeBKs()
                val followedStocks = Injector.appDatabase.stockDao().getFollowedStocks()
                val followBKs = Injector.appDatabase.bkDao().getFollowedBKS()
                launch (Dispatchers.Main){
                    (binding.rv.adapter as FollowAdapter).submitList((followBKs + followedStocks) as MutableList<Any>)
                }
                 delay(3000)
            }
        }

        return binding.root
    }

    inner class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {

            if (oldItem is Stock && newItem is Stock) {
                return oldItem.code == newItem.code
            }

            if (oldItem is BK && newItem is BK) {
                return oldItem.code == newItem.code
            }

            return false

        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is Stock && newItem is Stock) {
                return oldItem.chg == newItem.chg && oldItem.price==newItem.price
            }

            if (oldItem is BK && newItem is BK) {
                return oldItem.chg == newItem.chg && oldItem.price==newItem.price
            }

            return false
        }

    }

    inner class FollowAdapter() :
        ListAdapter<Any, FollowAdapter.VH>(DiffCallback()) {


        inner class VH(val binding: ItemFollowBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Any, position: Int) {
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

                    var ev: MotionEvent? = null
                    binding.root.setOnTouchListener { view, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                            ev = motionEvent
                        }
                        return@setOnTouchListener false
                    }

                    binding.root.setOnLongClickListener {
                        val b =
                            LayoutStockPopupWindowBinding.inflate(LayoutInflater.from(it.context))
                        b.followBtn.text = "取消关注"
                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )
                        b.followBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                Injector.appDatabase.followDao()
                                    .deleteFollow(Follow(item.code, 1))
                                launch(Dispatchers.Main) {
                                    val newList=  currentList.toMutableList().apply {
                                        remove(item)
                                    }
                                    submitList(newList)
                                }
                            }

                        }
                        pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -300)
                        return@setOnLongClickListener true
                    }
                }

                if (item is BK) {
                    binding.name.text = item.name
                    binding.chgTv.text = item.chg.toString() + "%"


                    if (item.chg > 0) {
                        binding.chgTv.setTextColor(Color.RED)
                    } else if (item.chg < 0) {
                        binding.chgTv.setTextColor(Color.parseColor("#ff00ad43"))
                    }

                    binding.root.setOnClickListener {
                        item.openWeb(it.context)
                    }
                    var ev: MotionEvent? = null
                    binding.root.setOnTouchListener { view, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                            ev = motionEvent
                        }
                        return@setOnTouchListener false
                    }

                    binding.root.setOnLongClickListener {
                        val b =
                            LayoutStockPopupWindowBinding.inflate(LayoutInflater.from(it.context))
                        b.followBtn.text = "取消关注"
                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )
                        b.followBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                Injector.appDatabase.followDao()
                                    .deleteFollow(Follow(item.code, 1))
                                launch(Dispatchers.Main) {

                                  val newList=  currentList.toMutableList().apply {
                                       remove(item)
                                   }
                                    submitList(newList)
                                }
                            }

                        }
                        pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -300)
                        return@setOnLongClickListener true
                    }

                }


            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemFollowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }


        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), position)
        }


    }


}