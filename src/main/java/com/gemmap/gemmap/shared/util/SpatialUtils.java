package com.gemmap.gemmap.shared.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;

/**
 * 공간 데이터 처리 유틸리티
 *
 * JTS를 사용하여 좌표를 Point 객체로 변환한다.
 * 좌표 순서는 (X, Y) = (경도, 위도)이다.
 */
public class SpatialUtils {

    private static final int SRID_WGS84 = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private SpatialUtils() {}

    /** BigDecimal 좌표를 Point 객체로 변환 */
    public static Point createPoint(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("좌표는 null일 수 없습니다.");
        }
        return createPoint(longitude.doubleValue(), latitude.doubleValue());
    }

    /** double 좌표를 Point 객체로 변환 */
    public static Point createPoint(double longitude, double latitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID_WGS84);
        return point;
    }

    public static GeometryFactory getGeometryFactory() {
        return GEOMETRY_FACTORY;
    }
}
