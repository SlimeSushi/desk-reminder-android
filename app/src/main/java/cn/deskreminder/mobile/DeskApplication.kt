package cn.deskreminder.mobile

import android.app.Application
import cn.deskreminder.mobile.data.DataRepository
import cn.deskreminder.mobile.data.cloud.CloudSync
import cn.deskreminder.mobile.reminder.ReminderScheduler
import cn.deskreminder.mobile.widget.Widgets
import kotlinx.coroutines.launch

class DeskApplication:Application(){
    val repository by lazy{DataRepository(this)}
    val reminders by lazy{ReminderScheduler(this,repository)}
    val cloud by lazy{CloudSync(this,repository)}
    override fun onCreate(){super.onCreate();val repo=repository;cloud;repo.onChanged={repo.scope.launch{reminders.reschedule();Widgets.update(this@DeskApplication,repo.current())}};repo.scope.launch{repo.current();reminders.ensureChannels();reminders.reschedule();Widgets.update(this@DeskApplication,repo.current())}}
}
