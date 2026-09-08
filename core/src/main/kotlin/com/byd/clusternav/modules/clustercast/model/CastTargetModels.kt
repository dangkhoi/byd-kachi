package com.byd.clusternav.modules.clustercast.model

enum class TargetClass { NORMAL, PROJECTION_SINK, KEEP_SESSION, UNKNOWN_PROTECTED }

/**
 * Cluster style, chosen per application exactly as V1 proved necessary: one global flag made every
 * app inherit the previous app's cluster shape. CURVED keeps the km/h gauge, RECT uses the full pane.
 */
enum class ClusterStyle { CURVED, RECT }
