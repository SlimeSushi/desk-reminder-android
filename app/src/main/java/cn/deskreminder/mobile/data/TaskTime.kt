package cn.deskreminder.mobile.data

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.floor

/** Same deadline and rounding rules as the installed desktop client's TaskDates.Format. */
object TaskTime {
    private fun duration(span:Duration,roundUp:Boolean):String{
        val total=span.seconds/60.0+span.nano/60_000_000_000.0
        val minutes=(if(roundUp)ceil(total)else floor(total)).toLong().coerceAtLeast(1)
        return when{
            minutes>=1440->"${minutes/1440}天"+(if(minutes%1440/60>0)"${minutes%1440/60}小时"else"")
            minutes>=60->"${minutes/60}小时"+(if(minutes%60>0)"${minutes%60}分"else"")
            else->"${minutes}分钟"
        }
    }
    fun status(due:LocalDateTime,now:LocalDateTime=LocalDateTime.now()):String?{
        val remaining=Duration.between(now,due)
        return when{
            remaining.isNegative->if(remaining.negated()<Duration.ofMinutes(1))"刚刚超时"else"已超时 "+duration(remaining.negated(),false)
            remaining<Duration.ofMinutes(1)->"即将到点"
            remaining<=Duration.ofHours(24)->"还剩 "+duration(remaining,true)
            else->null
        }
    }
    fun format(due:LocalDateTime,now:LocalDateTime=LocalDateTime.now()):String{
        val date=Dates.relative(due,now);val status=status(due,now)
        return if(due<now)"$status · $date" else date+(status?.let{" · $it"}?:"")
    }
}
