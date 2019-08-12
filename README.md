# pilot-server-gae
A variation of the pilot-server repo, designed to run on google app engine (gae)

pilot-server [https://github.com/sshahidi/pilot-server] is a simple Wifi based based localization engine that is supposed to be escalable

Here's the paper in which we have published the results: https://ieeexplore.ieee.org/abstract/document/8292265

The server uses received signal strenth (RSS) indicator of wifi accesspoints to locate the user, via applying the K-nearest neigbours algorithm on a databse of previously stored RSS values tagged with their GPS coordinates (more info about the algo, or the performance optimizations in the paper).
