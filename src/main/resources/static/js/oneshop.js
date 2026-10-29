/**
 * OneShop Cosmetics Chain - Client Scripts
 */

// Showrooms Data
const ONE_SHOP_BRANCHES = [
    {
        id: 'q1',
        name: 'OneShop Quận 1 - TP. Hồ Chí Minh',
        address: '120 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
        hotline: '0349846114',
        hours: '08:30 - 22:00',
        lat: 10.7816,
        lng: 106.6992,
        mapQuery: '120+Hai+Bà+Trưng,+Phường+Bến+Nghé,+Quận+1,+Hồ+Chí+Minh'
    },
    {
        id: 'td',
        name: 'OneShop Thủ Đức - TP. Hồ Chí Minh',
        address: '216 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh',
        hotline: '0369815844',
        hours: '08:30 - 22:00',
        lat: 10.8514,
        lng: 106.7720,
        mapQuery: '216+Võ+Văn+Ngân,+Phường+Linh+Chiểu,+Thủ+Đức,+Hồ+Chí+Minh'
    }
];

// Slideshow state
let currentHeroSlide = 0;
let heroSlideTimer = null;

// Function to switch hero slide
function gotoHeroSlide(index) {
    const slides = document.querySelectorAll('.os-showcase-slide');
    if (!slides.length) return;

    if (index < 0) index = slides.length - 1;
    if (index >= slides.length) index = 0;
    currentHeroSlide = index;

    slides.forEach((slide, i) => {
        if (i === currentHeroSlide) {
            slide.classList.add('active');
        } else {
            slide.classList.remove('active');
        }
    });

    const dots = document.querySelectorAll('.os-showcase-dot');
    dots.forEach((dot, i) => {
        dot.classList.toggle('active', i === currentHeroSlide);
    });
}

function startHeroSlideshow() {
    stopHeroSlideshow();
    heroSlideTimer = setInterval(() => {
        gotoHeroSlide(currentHeroSlide + 1);
    }, 3500); // changes every 3.5 seconds
}

function stopHeroSlideshow() {
    if (heroSlideTimer) {
        clearInterval(heroSlideTimer);
        heroSlideTimer = null;
    }
}

// Haversine formula to compute geodesic distance between two points in km
function calculateDistanceKm(lat1, lon1, lat2, lon2) {
    const R = 6371; // Earth radius in km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// Estimate travel duration by motorbike (avg 25 km/h in HCMC traffic)
function estimateTravelTime(distanceKm) {
    if (distanceKm < 1) return 'khoảng 3 - 5 phút';
    if (distanceKm < 3) return 'khoảng 8 - 12 phút';
    if (distanceKm < 7) return 'khoảng 15 - 25 phút';
    if (distanceKm < 15) return 'khoảng 30 - 45 phút';
    return 'khoảng 50+ phút';
}

// Open GPS Store Finder Modal
function openGpsStoreModal() {
    const modalEl = document.getElementById('gpsStoreModal');
    if (!modalEl) return;

    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.show();

    // Trigger GPS search
    requestUserGpsLocation();
}

let activeGpsRequestId = 0;

// Request Geolocation from Browser
function requestUserGpsLocation() {
    activeGpsRequestId++;
    const requestId = activeGpsRequestId;

    const locatingState = document.getElementById('gpsLocatingState');
    const resultsState = document.getElementById('gpsResultsState');
    const errorState = document.getElementById('gpsErrorState');

    if (locatingState) locatingState.classList.remove('d-none');
    if (resultsState) resultsState.classList.add('d-none');
    if (errorState) errorState.classList.add('d-none');

    if (!navigator.geolocation) {
        showGpsError('Thiết bị hoặc trình duyệt của bạn không hỗ trợ định vị GPS.');
        return;
    }

    navigator.geolocation.getCurrentPosition(
        function (pos) {
            if (requestId !== activeGpsRequestId) return;
            onGpsSuccess(pos);
        },
        function (err) {
            if (requestId !== activeGpsRequestId) return;
            onGpsError(err);
        },
        {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 0
        }
    );
}

function onGpsSuccess(position) {
    const locatingState = document.getElementById('gpsLocatingState');
    const resultsState = document.getElementById('gpsResultsState');
    const errorState = document.getElementById('gpsErrorState');
    const branchesContainer = document.getElementById('gpsBranchesList');
    const nearestTitle = document.getElementById('gpsNearestTitle');
    const nearestSubtitle = document.getElementById('gpsNearestSubtitle');

    if (locatingState) locatingState.classList.add('d-none');
    if (errorState) errorState.classList.add('d-none');
    if (resultsState) resultsState.classList.remove('d-none');

    const userLat = position.coords.latitude;
    const userLng = position.coords.longitude;

    // Calculate distance for each branch
    const computedBranches = ONE_SHOP_BRANCHES.map(b => {
        const distKm = calculateDistanceKm(userLat, userLng, b.lat, b.lng);
        return {
            ...b,
            distanceKm: distKm,
            formattedDist: distKm < 1 ? `${Math.round(distKm * 1000)} m` : `${distKm.toFixed(1)} km`,
            timeEstimate: estimateTravelTime(distKm)
        };
    });

    // Sort by nearest distance
    computedBranches.sort((a, b) => a.distanceKm - b.distanceKm);

    const nearest = computedBranches[0];
    if (nearestTitle) {
        nearestTitle.innerHTML = `Cửa hàng gần bạn nhất: <span class="text-danger">${nearest.name}</span> (${nearest.formattedDist})`;
    }
    if (nearestSubtitle) {
        nearestSubtitle.textContent = `Ước tính thời gian di chuyển: ${nearest.timeEstimate} xe máy. Nhấn chỉ đường để mở Google Maps!`;
    }

    // Render cards
    if (branchesContainer) {
        branchesContainer.innerHTML = computedBranches.map((b, index) => {
            const isNearest = index === 0;
            const googleMapUrl = `https://www.google.com/maps/dir/?api=1&origin=${userLat},${userLng}&destination=${b.lat},${b.lng}`;
            
            return `
                <div class="col-md-6">
                    <div class="os-store-card h-100 ${isNearest ? 'is-nearest' : ''}">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <div>
                                <span class="badge ${isNearest ? 'bg-danger' : 'bg-secondary'} rounded-pill px-3 py-1 mb-1">
                                    ${isNearest ? '<i class="bi bi-star-fill text-warning me-1"></i> GẦN BẠN NHẤT' : '<i class="bi bi-shop me-1"></i> CỬA HÀNG'}
                                </span>
                                <h6 class="fw-bold mb-0 text-dark">${b.name}</h6>
                            </div>
                            <span class="badge ${isNearest ? 'bg-danger-subtle text-danger border border-danger-subtle' : 'bg-light text-dark border'} fw-bold px-2 py-1 fs-6">
                                <i class="bi bi-pin-map-fill me-1"></i> ${b.formattedDist}
                            </span>
                        </div>
                        
                        <p class="text-muted small mb-2">
                            <i class="bi bi-geo-alt text-danger me-1"></i> ${b.address}
                        </p>
                        
                        <div class="small text-secondary mb-3">
                            <div><i class="bi bi-clock me-1 text-primary"></i> Mở cửa: <strong>${b.hours}</strong> hàng ngày</div>
                            <div><i class="bi bi-speedometer2 me-1 text-success"></i> Thời gian đi: <strong>${b.timeEstimate}</strong></div>
                        </div>

                        <div class="d-flex gap-2 pt-2 border-top">
                            <a href="${googleMapUrl}" target="_blank" rel="noopener noreferrer" class="btn btn-sm ${isNearest ? 'btn-danger' : 'btn-outline-dark'} flex-grow-1">
                                <i class="bi bi-arrow-up-right-square me-1"></i> Chỉ đường Maps
                            </a>
                            <a href="tel:${b.hotline}" class="btn btn-sm btn-outline-success px-3 fw-bold" title="Gọi hotline">
                                <i class="bi bi-telephone-fill me-1"></i> ${b.hotline}
                            </a>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }
}

function onGpsError(error) {
    let msg = 'Không thể xác định vị trí của bạn.';
    switch (error.code) {
        case error.PERMISSION_DENIED:
            msg = 'Bạn đã từ chối quyền truy cập vị trí trên trình duyệt. Vui lòng cho phép quyền vị trí để hệ thống tự động tìm cửa hàng gần nhất.';
            break;
        case error.POSITION_UNAVAILABLE:
            msg = 'Tín hiệu GPS thiết bị hiện không khả dụng. Quý khách vui lòng thử lại sau.';
            break;
        case error.TIMEOUT:
            msg = 'Quá thời gian kết nối GPS. Vui lòng thử lại hoặc xem địa chỉ cửa hàng bên dưới.';
            break;
    }
    showGpsError(msg);
}

function showGpsError(message) {
    const locatingState = document.getElementById('gpsLocatingState');
    const resultsState = document.getElementById('gpsResultsState');
    const errorState = document.getElementById('gpsErrorState');
    const errorMsgEl = document.getElementById('gpsErrorMessage');
    const fallbackList = document.getElementById('gpsFallbackList');

    if (locatingState) locatingState.classList.add('d-none');
    if (resultsState) resultsState.classList.add('d-none');
    if (errorState) errorState.classList.remove('d-none');
    if (errorMsgEl) errorMsgEl.textContent = message;

    // Render fallback list
    if (fallbackList) {
        fallbackList.innerHTML = ONE_SHOP_BRANCHES.map(b => `
            <div class="col-md-6">
                <div class="os-store-card h-100">
                    <span class="badge bg-secondary rounded-pill px-3 py-1 mb-2">Cửa hàng</span>
                    <h6 class="fw-bold mb-1">${b.name}</h6>
                    <p class="text-muted small mb-2"><i class="bi bi-geo-alt text-danger me-1"></i> ${b.address}</p>
                    <div class="small text-secondary mb-3"><i class="bi bi-clock me-1"></i> ${b.hours}</div>
                    <div class="d-flex gap-2">
                        <a href="https://www.google.com/maps/search/?api=1&query=${b.mapQuery}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-dark flex-grow-1">
                            <i class="bi bi-map me-1"></i> Xem trên Google Maps
                        </a>
                        <a href="tel:${b.hotline}" class="btn btn-sm btn-outline-danger px-3 fw-bold">
                            <i class="bi bi-telephone-fill me-1"></i> ${b.hotline}
                        </a>
                    </div>
                </div>
            </div>
        `).join('');
    }
}

// Make functions globally accessible
window.openGpsStoreModal = openGpsStoreModal;
window.requestUserGpsLocation = requestUserGpsLocation;
window.gotoHeroSlide = gotoHeroSlide;

document.addEventListener('DOMContentLoaded', function () {
    // Auto-dismiss alerts after 5 seconds
    const alerts = document.querySelectorAll('.alert-dismissible');
    alerts.forEach(function (alert) {
        setTimeout(function () {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            if (bsAlert) bsAlert.close();
        }, 5000);
    });

    // Quantity Increment / Decrement
    const qtyInputs = document.querySelectorAll('.quantity-input');
    qtyInputs.forEach(function (input) {
        const decBtn = input.parentElement.querySelector('.btn-qty-minus');
        const incBtn = input.parentElement.querySelector('.btn-qty-plus');

        if (decBtn) {
            decBtn.addEventListener('click', function () {
                let currentVal = parseInt(input.value) || 1;
                if (currentVal > 1) {
                    input.value = currentVal - 1;
                    if (input.form && input.dataset.autosubmit === 'true') {
                        input.form.submit();
                    }
                }
            });
        }

        if (incBtn) {
            incBtn.addEventListener('click', function () {
                let currentVal = parseInt(input.value) || 1;
                input.value = currentVal + 1;
                if (input.form && input.dataset.autosubmit === 'true') {
                    input.form.submit();
                }
            });
        }
    });

    // Start Hero Cosmetics Slideshow if present
    const showcaseFrame = document.getElementById('heroCosmeticsShowcase');
    if (showcaseFrame) {
        startHeroSlideshow();

        // Pause slideshow on hover, resume on mouse leave
        showcaseFrame.addEventListener('mouseenter', stopHeroSlideshow);
        showcaseFrame.addEventListener('mouseleave', startHeroSlideshow);
    }

    // Wishlist Toggle Handler
    document.querySelectorAll('.btn-wishlist-toggle').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            const productId = this.dataset.productId;
            if (!productId) return;

            const icon = this.querySelector('i');
            fetch('/wishlist/toggle', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({ productId: productId })
            })
            .then(res => {
                if (res.status === 401 || res.status === 403) {
                    window.location.href = '/auth/login?redirect=' + encodeURIComponent(window.location.pathname);
                    return null;
                }
                return res.json();
            })
            .then(data => {
                if (!data) return;
                if (data.requireLogin) {
                    window.location.href = '/auth/login?redirect=' + encodeURIComponent(window.location.pathname);
                    return;
                }
                if (data.success) {
                    if (data.liked) {
                        if (icon) icon.className = 'bi bi-heart-fill fs-5 text-danger';
                        btn.classList.add('active', 'bg-danger-subtle', 'text-danger');
                    } else {
                        if (icon) icon.className = 'bi bi-heart fs-5';
                        btn.classList.remove('active', 'bg-danger-subtle', 'text-danger');
                    }

                    // Update header badge dynamically
                    let badge = document.getElementById('globalWishlistBadge');
                    if (data.totalCount > 0) {
                        if (!badge) {
                            const wishlistLink = document.querySelector('a[href="/wishlist"]');
                            if (wishlistLink) {
                                badge = document.createElement('span');
                                badge.id = 'globalWishlistBadge';
                                badge.className = 'os-badge-count bg-danger';
                                wishlistLink.appendChild(badge);
                            }
                        }
                        if (badge) badge.textContent = data.totalCount;
                    } else if (badge) {
                        badge.remove();
                    }

                    showWishlistNotification(data.message, data.liked);
                }
            })
            .catch(err => console.error('Wishlist error:', err));
        });
    });

    function showWishlistNotification(message, isLiked) {
        let toast = document.getElementById('osWishlistToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'osWishlistToast';
            toast.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#1e293b;color:#fff;padding:12px 24px;border-radius:50px;box-shadow:0 10px 25px rgba(0,0,0,0.25);z-index:99999;font-size:14px;font-weight:600;display:flex;align-items:center;gap:10px;transition:opacity 0.3s;pointer-events:none;';
            document.body.appendChild(toast);
        }
        toast.innerHTML = (isLiked ? '<i class="bi bi-heart-fill text-danger fs-5"></i> ' : '<i class="bi bi-heartbreak text-secondary fs-5"></i> ') + message;
        toast.style.opacity = '1';
        setTimeout(() => {
            toast.style.opacity = '0';
        }, 2500);
    }
});

