#!/bin/bash
P=NetconfServerSimulator
cd ..
sudo wtemulator --config=$P/config.json --topo=$P/topology.json --xml=yang/microwave-model-config.xml
