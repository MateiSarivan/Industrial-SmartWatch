import socket
import time
import uuid
from threading import Thread
from threading import Lock

lock = Lock()


class Device():
    def __init__(self):
        self.status = False
        self.device_id = None
        self.port = None
        self.thread_on = True
        self.sending_message = False
        self.com_thread = Thread(target=self.communication)
        self.message = "Status=NEUTRAL\n"
        self.pending_tasks = []
        self.acquired_tasks = []
        self.completed_tasks = []

    def get_id(self):
        return self.device_id

    def set_status(self, status):
        lock.acquire()
        self.status = status
        lock.release()

    def get_status(self):
        return self.status

    def set_message (self, message):
        lock.acquire()
        self.message = message
        self.sending_message = True
        lock.release()

    def remove_task(self, task_id):
        self.completed_tasks.remove(task_id)
        

    def remove_pending_task(self, task_id):
        print("Attempting removal")
        if task_id in self.pending_tasks:
            print("Removing " + task_id + "from these tasks: ")
            print(self.pending_tasks)
            print("from")
            print(self.device_id)
            self.pending_tasks.remove(task_id)
            message = "TASK_DISCARD:{" + task_id + "}\n"        
            self.set_message(message)

    # def send_tasks(self, tasks):
    #     message = "Tasks: "
    #     for task in tasks:
            
    def new_task(self, task):
        task_id = task.get_id()
        message = "TASK:{" + task.get_origin() + ";" + task.get_description() + ";" + task.get_urgency() + ";" + task_id + "}\n"
        print("Sent message to " + self.device_id)
        self.set_message(message)
        self.pending_tasks.append(task_id)

    def communication(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        server_address = ('', self.port)
        sock.bind(server_address)
        sock.settimeout(3)
        while True:
            try:
                lock.acquire()
                com_active = self.thread_on
                sent_message = self.sending_message
                lock.release()
                if not com_active:
                    break
                else:
                    data, address = sock.recvfrom(32)
                    if sent_message:
                        if "MESSOK\n" in data.decode():
                            lock.acquire()
                            self.sending_message = False
                            self.message = "Status=NEUTRAL\n"
                            lock.release()
                    if "ACQUIRE:" in data.decode():
                        
                        lock.acquire()
                        self.message = "Status=RECEIVED_MESSAGE\n"
                        self.sending_message = True
                        lock.release()
                        task_id = data.decode().strip("ACQUIRE:")
                        task_id = task_id.strip("\n")
                        for p_d in self.pending_tasks:
                            if task_id in p_d:
                                self.pending_tasks.remove(p_d)
                                self.acquired_tasks.append(p_d)
                                break
                        print("task acquired: " + task_id)
                    elif "COMPLETED:" in data.decode():
                        
                        lock.acquire()
                        self.message = "Status=RECEIVED_MESSAGE\n"
                        self.sending_message = True
                        lock.release()
                        task_id = data.decode().strip("COMPLETED:")
                        task_id = task_id.strip("\n")
                        for p_d in self.acquired_tasks:
                            if task_id in p_d:
                                self.acquired_tasks.remove(p_d)
                                self.completed_tasks.append(p_d)
                                break
                        print("task completed: " + task_id)
                            

                    sock.sendto(bytes(self.message, "UTF-8"), address)
            except socket.timeout:
                print("Device " + self.device_id + " has disconnected.")
                self.set_status(False)
                break
        

class Task():
    def __init__(self, origin, description, urgency):
        self.taskId = str(uuid.uuid1()).split('-')[0]
        self.taskStatus = "Pending"
        self.taskDescription = description
        self.taskOrigin = origin
        self.taskUrgency = urgency

    def get_id(self):
        return self.taskId

    def get_description(self):
        return self.taskDescription

    def get_origin(self):
        return self.taskOrigin

    def get_urgency(self):
        return self.taskUrgency

    def get_status(self):
        return self.taskStatus

    def set_status(self, status):
        self.taskStatus = status

    def get_text(self):
        return self.taskOrigin + " " + self.taskDescription + " " + self.taskUrgency


class SmartWatchServer():
    def __init__(self):
        self.thread_on = True
        self.devices = []
        self.communication_active = True
        self.tasks = []
        x = Thread(target=self.communication)
        x.start()

        

    def new_task(self, origin, description, urgency):
        task = Task(origin, description, urgency)
        self.tasks.append(task)
        for device in self.devices:
            device.new_task(task)
            time.sleep(0.1)
        return task.get_id()

    def get_tasks(self):
        return self.tasks
    
    def get_no_connected_devices(self):
        return len(self.devices)

    def get_devices(self):
        return self.devices

    def update_tasks(self):
        for device in self.devices:
            for task_id in device.acquired_tasks:
                for device_2 in self.devices:
                    if device.get_id() not in device_2.get_id():
                        device_2.remove_pending_task(task_id)
                for task in self.tasks:
                    if task_id in task.get_id():
                        task.set_status("Acquired")
                    
        for device in self.devices:
            for task_id in device.completed_tasks:
                for task in self.tasks:
                    if task_id in task.get_id():
                        device.remove_task(task_id)
                        self.tasks.remove(task)
                        
    def send_tasks(self, device):
        print(self.tasks)
        for task in self.tasks:
            print("Holla")
            if "Pending" in task.get_status():
                print(task.get_id())
                device.new_task(task)
                time.sleep(1.5)

    def close_communication(self):
        lock.acquire()
        self.communication_active = False
        lock.release()
        
    def get_available_port(self):
        for candidate_port in range(50001, 60000):
            port_free = True
            for device in self.devices:
                if device.port == candidate_port:
                    port_free = False
            if port_free:
                break
        return candidate_port

    def disconnect_devices(self):
        for device in self.devices:
            lock.acquire()
            device.thread_on = False
            lock.release()
        self.devices = []
        print('thread signal ')

    def clear_innactive_devices(self):
        index = 0
        while True:
            all_active = True
            for i in range(0, len(self.devices)):
                if not self.devices[i].get_status():
                    print("Device not active")
                    all_active = False
                    break
            if not all_active:
                print(i)
                print("Device with ID " + self.devices[i].device_id + " will be removed")
                self.devices.pop(i)
            if all_active:
                break

    def communication(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        server_address = ('', 50000)
        sock.settimeout(2)
        sock.bind(server_address)
        print("Started communication thread...")
        while True:
            time.sleep(0.1)
            self.update_tasks()
            self.clear_innactive_devices()
            try:
                lock.acquire()
                com_active = self.communication_active
                lock.release()
                if not com_active:
                    break
                else:
                    data, address = sock.recvfrom(32)
                    print(address)
                    print(data)
                    data = data.decode()
                    device_id = data.strip("Device_ID=")
                    device_id = data.strip("\n")
                    print(device_id)
                    port = self.get_available_port()
                    sock.sendto(bytes(str(port) + "\n", "UTF-8"), address)
                    device = Device()
                    device.port = port
                    device.device_id = device_id
                    device.set_status(True)
                    device.com_thread.start()
                    time.sleep(0.5)
                    tasks_thread = Thread(target=self.send_tasks, args=(device,))
                    tasks_thread.start()
                    self.devices.append(device)
            except socket.timeout:
                pass
        sock.close()
        print("Communication closed")
